package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

    private final Clock clock;
    private final ProfilingState profilingState;
    private final Object targetObject;

    ProfilingMethodInterceptor(Clock clock, ProfilingState profilingState, Object targetObject) {
        this.clock = Objects.requireNonNull(clock);
        this.profilingState = Objects.requireNonNull(profilingState);
        this.targetObject = Objects.requireNonNull(targetObject);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isAnnotationPresent(Profiled.class)) {
            long currentTime = clock.instant().toEpochMilli();
            Object invokedObject;
            try {
                invokedObject = method.invoke(targetObject, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            } finally {
                profilingState.record(targetObject.getClass(), method, Duration.ofMillis(clock.instant().toEpochMilli() - currentTime));
            }
            return invokedObject;
        }
        return method.invoke(targetObject, args);
    }
}
