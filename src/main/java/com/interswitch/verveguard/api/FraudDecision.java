package com.interswitch.verveguard.api;

public enum FraudDecision {
    ALLOW,   // Score below review threshold
    REVIEW,  // Score between review and block threshold
    BLOCK    // Score above block threshold, or hard-blocked
}

