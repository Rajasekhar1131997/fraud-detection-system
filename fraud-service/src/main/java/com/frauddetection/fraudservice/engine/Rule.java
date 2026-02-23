package com.frauddetection.fraudservice.engine;

import com.frauddetection.fraudservice.event.TransactionCreatedEvent;

public interface Rule {

    String name();

    double weight();

    double evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext);
}
