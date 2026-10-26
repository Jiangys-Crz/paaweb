package com.cwnupaa.backend.config;

import java.lang.annotation.*;

/** Queue before opening a transaction; the first argument contains the event ID. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SignupWriteGuard { }
