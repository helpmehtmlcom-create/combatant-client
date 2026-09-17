/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming;

import combatant.client.features.module.Module;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Predicate;
/**
 * Provider-aware request handler.
 * <p>
 * Adapted from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */
public final class RequestHandler<T> {

    @SuppressWarnings("rawtypes")
    private static final Comparator<Request> PRIORITY_COMPARATOR =
            (req1, req2) -> Integer.compare(req2.priority, req1.priority);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private final PriorityBlockingQueue<Request<T>> activeRequests =
            new PriorityBlockingQueue<>(11, (Comparator) PRIORITY_COMPARATOR);
    private int currentTick = 0;

    private static final class ProviderPredicate<T> implements Predicate<Request<T>> {
        private Object targetProvider;

        void setTarget(Object provider) {
            this.targetProvider = provider;
        }

        @Override
        public boolean test(Request<T> request) {
            return request != null && request.provider == targetProvider;
        }
    }

    private final ThreadLocal<ProviderPredicate<T>> providerPredicate =
            ThreadLocal.withInitial(ProviderPredicate::new);

    private boolean removeByProvider(Object provider) {
        ProviderPredicate<T> predicate = providerPredicate.get();
        predicate.setTarget(provider);
        try {
            return activeRequests.removeIf(predicate);
        } finally {
            predicate.setTarget(null);
        }
    }
    private static boolean isProviderRunning(Object provider) {
        return !(provider instanceof Module module) || module.isEnabled();
    }

    public void tick() {
        currentTick++;
    }

    public void request(Request<T> request) {
        if (request == null) return;

        if (!activeRequests.isEmpty()) {
            removeByProvider(request.provider);
        }
        request.expiresIn += currentTick;
        activeRequests.add(request);
    }

    public void request(int expiresIn, int priority, Object provider, T value) {
        request(new Request<>(expiresIn, priority, provider, value));
    }

    public Request<T> getActiveRequest() {
        Request<T> top = activeRequests.peek();
        if (top == null || (top.expiresIn > currentTick && isProviderRunning(top.provider))) {
            return top;
        }
        do {
            activeRequests.poll();
            top = activeRequests.peek();
        } while (top != null && (top.expiresIn <= currentTick || !isProviderRunning(top.provider)));
        return top;
    }

    public T getActiveRequestValue() {
        Request<T> top = getActiveRequest();
        return top != null ? top.value : null;
    }

    public Object getActiveRequestProvider() {
        Request<T> top = getActiveRequest();
        return top != null ? top.provider : null;
    }

    public void clear() {
        activeRequests.clear();
        currentTick = 0;
    }

    public boolean clear(Object provider) {
        if (provider == null || activeRequests.isEmpty()) {
            return false;
        }
        return removeByProvider(provider);
    }

    public static final class Request<T> {
        private final int priority;
        private final Object provider;
        private final T value;
        private int expiresIn;

        public Request(int expiresIn, int priority, Object provider, T value) {
            this.expiresIn = expiresIn;
            this.priority = priority;
            this.provider = provider;
            this.value = value;
        }

        public Object provider() {
            return provider;
        }

        public T value() {
            return value;
        }
    }
}
