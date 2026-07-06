package io.paylab.api;

/**
 * Phase 0 liveness facade. Every PayLab service publishes this over bolt so that service
 * registration into SOFARegistry is observable before any real domain RPC exists.
 */
public interface PingFacade {

    String ping(String from);
}
