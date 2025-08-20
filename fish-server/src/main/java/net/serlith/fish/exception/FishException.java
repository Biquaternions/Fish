package net.serlith.fish.exception;

public class FishException extends RuntimeException {

    public FishException(String... lines) {
        super(String.join("\n", lines));
    }

}
