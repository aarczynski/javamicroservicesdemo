#!/bin/sh

while true; do
    k6 run /scripts/candidate-search.js
done
