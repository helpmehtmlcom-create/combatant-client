/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

import combatant.client.runtime.error.FailureBoundary;

import combatant.client.runtime.error.FailureIsolation;
import combatant.client.runtime.error.FailureExecution;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import combatant.client.features.module.Module;
import combatant.client.runtime.RuntimeGate;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.util.logging.DebugLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public final class EventBus {
    private static final Subscriber[] EMPTY_SUBSCRIBERS = new Subscriber[0];
    private static final ScannedHandler[] EMPTY_HANDLERS = new ScannedHandler[0];
    private static final Comparator<Subscriber> PRIORITY =
            Comparator.comparingInt((Subscriber sub) -> sub.priority).reversed();
    private static final MethodType ADAPTED_UNBOUND_TYPE =
            MethodType.methodType(void.class, Object.class, Event.class);
    private static final MethodType ADAPTED_STATIC_TYPE =
            MethodType.methodType(void.class, Event.class);

    private static final ClassValue<MethodHandles.Lookup> LOOKUP_CACHE = new ClassValue<>() {
        @Override
        protected MethodHandles.Lookup computeValue(Class<?> type) {
            try {
                return MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            } catch (Throwable t) {
                return MethodHandles.lookup();
            }
        }
    };

    private static final ConcurrentHashMap<Method, MethodHandle> UNBOUND_INVOKER_CACHE = new ConcurrentHashMap<>();

    private record ScannedHandler(
            Method method,
            Class<? extends Event> eventType,
            int priority
    ) {}

    private static final ClassValue<ScannedHandler[]> CLASS_HANDLERS = new ClassValue<>() {
        @Override
        protected ScannedHandler[] computeValue(Class<?> type) {
            List<ScannedHandler> list = new ArrayList<>();
            Set<String> seenMethods = new HashSet<>();

            for (Class<?> cls = type; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (Method method : cls.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(EventHandler.class)) continue;

                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 1) continue;
                    if (!Event.class.isAssignableFrom(params[0])) continue;

                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventType = (Class<? extends Event>) params[0];

                    String signature = method.getName() + "(" + params[0].getName() + ")";
                    if (!seenMethods.add(signature)) continue;

                    EventHandler meta = method.getAnnotation(EventHandler.class);
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {}

                    list.add(new ScannedHandler(method, eventType, meta.priority()));
                }
            }

            return list.isEmpty() ? EMPTY_HANDLERS : list.toArray(ScannedHandler[]::new);
        }
    };

    private final Object lock = new Object();

    private final Reference2ObjectOpenHashMap<Class<?>, Subscriber[]> exactHandlers = new Reference2ObjectOpenHashMap<>();
    /*
     * Posting is overwhelmingly more frequent than registration. In particular collision
     * hooks can post hundreds of events per client tick. Keeping this cache behind the
     * registration lock made every already-resolved dispatch enter a monitor for no reason.
     * The exact handler table is still mutated under lock; published flattened arrays are
     * immutable snapshots and can therefore be read lock-free.
     */
    private final ConcurrentHashMap<Class<?>, Subscriber[]> dispatchCache = new ConcurrentHashMap<>();
    private final Reference2ObjectOpenHashMap<Object, Subscriber[]> ownerIndex = new Reference2ObjectOpenHashMap<>();

    private static Subscriber[] append(Subscriber[] old, Subscriber sub) {
        final int oldLen = old.length;
        if (oldLen == 0) {
            return new Subscriber[]{ sub };
        }
        Subscriber[] next = Arrays.copyOf(old, oldLen + 1);
        next[oldLen] = sub;
        return next;
    }

    private static Subscriber[] remove(Subscriber[] old, Subscriber sub) {
        final int oldLen = old.length;
        if (oldLen == 0) return old;

        int index = -1;
        for (int i = 0; i < oldLen; i++) {
            if (old[i] == sub) {
                index = i;
                break;
            }
        }

        if (index < 0) return old;
        if (oldLen == 1) return EMPTY_SUBSCRIBERS;

        Subscriber[] next = new Subscriber[oldLen - 1];
        if (index > 0) {
            System.arraycopy(old, 0, next, 0, index);
        }
        int remaining = oldLen - index - 1;
        if (remaining > 0) {
            System.arraycopy(old, index + 1, next, index, remaining);
        }
        return next;
    }

    private static void sortSubscribers(Subscriber[] subscribers) {
        Arrays.sort(subscribers, PRIORITY);
    }

    public void register(Object listener) {
        if (listener instanceof Module module) {
            registerOwned(module, listener);
        } else {
            registerOwned(null, listener, FailureBoundary.ISOLATE);
        }
    }

    public void registerOwned(Module gateModule, Object listener) {
        registerOwned(gateModule, listener, FailureBoundary.ISOLATE);
    }

    /** Registers an independently owned service whose failures can be quarantined safely. */
    public void registerIsolated(Object listener) {
        registerOwned(null, listener, FailureBoundary.ISOLATE);
    }

    private void registerOwned(Module gateModule, Object listener, FailureBoundary boundary) {
        if (listener == null) return;

        synchronized (lock) {
            if (ownerIndex.containsKey(listener)) return;

            Subscriber[] subs = scan(listener, gateModule, boundary);
            if (subs.length == 0) return;

            ownerIndex.put(listener, subs);
            for (Subscriber sub : subs) {
                Subscriber[] old = exactHandlers.get(sub.eventType);
                if (old == null) old = EMPTY_SUBSCRIBERS;
                Subscriber[] next = append(old, sub);
                sortSubscribers(next);
                exactHandlers.put(sub.eventType, next);
            }

            dispatchCache.clear();
        }
    }

    public void unregister(Object listener) {
        if (listener == null) return;

        synchronized (lock) {
            Subscriber[] subs = ownerIndex.remove(listener);
            if (subs == null || subs.length == 0) return;

            for (Subscriber sub : subs) {
                Subscriber[] old = exactHandlers.get(sub.eventType);
                if (old == null || old.length == 0) continue;

                Subscriber[] next = remove(old, sub);
                if (next.length == 0) {
                    exactHandlers.remove(sub.eventType);
                } else {
                    exactHandlers.put(sub.eventType, next);
                }
            }

            dispatchCache.clear();
        }
    }

    public boolean hasListeners(Class<? extends Event> eventType) {
        return subscribersFor(eventType).length != 0;
    }

    public <T extends Event> T post(T event) {
        if (event == null) return null;
        if (!RuntimeGate.canRunClientLogic()) return event;

        Subscriber[] subscribers = subscribersFor(event.getClass());
        final int len = subscribers.length;
        if (len == 0) return event;

        if (!ProfilerPhase.isActive()) {
            dispatchUnprofiled(event, subscribers);
            return event;
        }

        try (ProfilerPhase.Scope eventScope = ProfilerPhase.scope("event:" + event.getClass().getSimpleName())) {
            for (int i = 0; i < len; i++) {
                Subscriber sub = subscribers[i];
                Module gate = sub.gateModule;
                if (gate != null && !gate.isEnabled()) continue;
                if (sub.boundary == FailureBoundary.ISOLATE && !ErrorHandler.canRun(sub.owner)) continue;

                try (ProfilerPhase.Scope handlerScope = ProfilerPhase.scope(sub.profileLabel)) {
                    sub.invoker.invoke(event);
                } catch (Throwable t) {
                    handleFailure(sub, t);
                }
            }
        }

        return event;
    }

    private static void dispatchUnprofiled(Event event, Subscriber[] subscribers) {
        final int len = subscribers.length;
        if (len == 0) return;

        for (int i = 0; i < len; i++) {
            Subscriber sub = subscribers[i];
            Module gate = sub.gateModule;
            if (gate != null && !gate.isEnabled()) continue;
            if (sub.boundary == FailureBoundary.ISOLATE && !ErrorHandler.canRun(sub.owner)) continue;

            try {
                sub.invoker.invoke(event);
            } catch (Throwable t) {
                handleFailure(sub, t);
            }
        }
    }

    private static void handleFailure(Subscriber sub, Throwable cause) {
        if (cause instanceof InvocationTargetException ite && ite.getCause() != null) {
            cause = ite.getCause();
        }
        FailureBoundary.requireRecoverable(cause);
        if (sub.gateModule != null) {
            FailureIsolation.reportModule(sub.gateModule, "event " + sub.describe(), cause);
        } else if (sub.boundary == FailureBoundary.ISOLATE) {
            FailureExecution.reportComponent(sub.owner, sub.owner.getClass().getSimpleName(),
                    "event " + sub.describe(), cause, FailureBoundary.ISOLATE);
        } else {
            // Non-module listeners may not have reversible state. Do not quarantine
            // an entire shared service or claim that its state has been recovered.
            DebugLog.error("Event handler failed: %s", cause, sub.describe());
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unisolated event handler failed: " + sub.describe(), cause);
        }
    }

    public void clear() {
        synchronized (lock) {
            exactHandlers.clear();
            dispatchCache.clear();
            ownerIndex.clear();
        }
    }

    public int listenerCount() {
        synchronized (lock) {
            return ownerIndex.size();
        }
    }

    private Subscriber[] scan(Object listener, Module gateModule, FailureBoundary boundary) {
        ScannedHandler[] handlers = CLASS_HANDLERS.get(listener.getClass());
        if (handlers.length == 0) return EMPTY_SUBSCRIBERS;

        Subscriber[] out = new Subscriber[handlers.length];
        for (int i = 0; i < handlers.length; i++) {
            ScannedHandler h = handlers[i];
            EventInvoker invoker = createInvoker(listener, h.method);
            out[i] = new Subscriber(listener, gateModule, boundary, h.method, invoker, h.eventType, h.priority);
        }
        return out;
    }

    private static MethodHandle getUnboundHandle(Method method) {
        MethodHandle cached = UNBOUND_INVOKER_CACHE.get(method);
        if (cached != null) return cached;

        try {
            method.setAccessible(true);
            MethodHandles.Lookup lookup = LOOKUP_CACHE.get(method.getDeclaringClass());
            MethodHandle handle = lookup.unreflect(method);
            MethodHandle adapted;
            if (Modifier.isStatic(method.getModifiers())) {
                adapted = handle.asType(ADAPTED_STATIC_TYPE);
            } else {
                adapted = handle.asType(ADAPTED_UNBOUND_TYPE);
            }
            UNBOUND_INVOKER_CACHE.put(method, adapted);
            return adapted;
        } catch (Throwable t) {
            return null;
        }
    }

    private static EventInvoker createInvoker(Object listener, Method method) {
        MethodHandle unbound = getUnboundHandle(method);
        if (unbound != null) {
            try {
                if (Modifier.isStatic(method.getModifiers())) {
                    return new MethodHandleInvoker(unbound);
                }
                MethodHandle bound = unbound.bindTo(listener);
                return new MethodHandleInvoker(bound);
            } catch (Throwable ignored) {
                // fall through to reflection invoker
            }
        }
        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {}
        return new ReflectionInvoker(method, listener);
    }

    private Subscriber[] subscribersFor(Class<?> eventType) {
        if (eventType == null) return EMPTY_SUBSCRIBERS;

        Subscriber[] cached = dispatchCache.get(eventType);
        if (cached != null) return cached;

        synchronized (lock) {
            cached = dispatchCache.get(eventType);
            if (cached != null) return cached;

            Subscriber[] built = buildFlattenedSubscribers(eventType);
            dispatchCache.put(eventType, built);
            return built;
        }
    }

    private Subscriber[] buildFlattenedSubscribers(Class<?> eventType) {
        Subscriber[] first = null;
        Subscriber[][] rest = null;
        int restCount = 0;
        int total = 0;

        Class<?> type = eventType;
        while (type != null && Event.class.isAssignableFrom(type)) {
            Subscriber[] exact = exactHandlers.get(type);
            if (exact != null && exact.length > 0) {
                if (first == null) {
                    first = exact;
                    total = exact.length;
                } else {
                    if (rest == null) {
                        rest = new Subscriber[4][];
                    } else if (restCount >= rest.length) {
                        rest = Arrays.copyOf(rest, rest.length * 2);
                    }
                    rest[restCount++] = exact;
                    total += exact.length;
                }
            }
            type = type.getSuperclass();
        }

        if (total == 0) return EMPTY_SUBSCRIBERS;
        if (restCount == 0) return first;

        Subscriber[] out = new Subscriber[total];
        System.arraycopy(first, 0, out, 0, first.length);
        int offset = first.length;
        for (int i = 0; i < restCount; i++) {
            Subscriber[] arr = rest[i];
            System.arraycopy(arr, 0, out, offset, arr.length);
            offset += arr.length;
        }

        sortSubscribers(out);
        return out;
    }

    @FunctionalInterface
    private interface EventInvoker {
        void invoke(Event event) throws Throwable;
    }

    private static final class MethodHandleInvoker implements EventInvoker {
        private final MethodHandle handle;

        MethodHandleInvoker(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public void invoke(Event event) throws Throwable {
            handle.invokeExact(event);
        }
    }

    private static final class ReflectionInvoker implements EventInvoker {
        private final Method method;
        private final Object listener;

        ReflectionInvoker(Method method, Object listener) {
            this.method = method;
            this.listener = listener;
        }

        @Override
        public void invoke(Event event) throws Throwable {
            try {
                method.invoke(listener, event);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                throw target != null ? target : e;
            }
        }
    }

    private static final class Subscriber {
        final Object owner;
        final Module gateModule;
        final FailureBoundary boundary;
        final Method method;
        final EventInvoker invoker;
        final Class<? extends Event> eventType;
        final int priority;
        final String description;
        final String profileLabel;

        Subscriber(Object owner,
                   Module gateModule,
                   FailureBoundary boundary,
                   Method method,
                   EventInvoker invoker,
                   Class<? extends Event> eventType,
                   int priority) {
            this.owner = owner;
            this.gateModule = gateModule;
            this.boundary = boundary;
            this.method = method;
            this.invoker = invoker;
            this.eventType = eventType;
            this.priority = priority;
            this.description = owner.getClass().getName() + "#" + method.getName();
            this.profileLabel = "handler:" + owner.getClass().getSimpleName() + "#" + method.getName();
        }

        String describe() {
            return description;
        }
    }
}
