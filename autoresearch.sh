#!/usr/bin/env bash

# Run the client and pipe output
# We expect the client to print METRIC startup_time=... and exit.
# To make this work, we will inject a small callback into Combatant.java
# that listens to CLIENT_STARTED, prints the time, and stops the game.

OUTPUT=$(bash gradlew runClient 2>&1)

# Extract the metric
METRIC_LINE=$(echo "$OUTPUT" | grep "METRIC startup_time=" | tail -n 1)

if [ -z "$METRIC_LINE" ]; then
    echo "Failed to find metric in output!"
    echo "$OUTPUT"
    exit 1
fi

echo "$METRIC_LINE"
exit 0
