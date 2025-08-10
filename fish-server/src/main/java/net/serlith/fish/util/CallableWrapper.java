package net.serlith.fish.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CallableWrapper<T> implements Runnable {

    private final Callable<T> callable;
    private final CompletableFuture<T> future = new CompletableFuture<>();

    public CallableWrapper(Callable<T> callable) {
        this.callable = callable;
    }

    @Override
    public final void run() {
        try {
            future.complete(callable.call());
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }
    }

    public final T get() {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
