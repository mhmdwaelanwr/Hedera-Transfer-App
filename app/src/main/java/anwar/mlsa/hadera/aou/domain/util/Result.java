package anwar.mlsa.hadera.aou.domain.util;

public abstract class Result<T> {
    private Result() {}

    public static final class Success<T> extends Result<T> {
        public final T data;

        public Success(T data) {
            this.data = data;
        }
    }

    public static final class Error<T> extends Result<T> {
        public final String message;

        public Error(String message) {
            this.message = message;
        }
    }

    public static final class Loading<T> extends Result<T> {
        public Loading() {}
    }
}
