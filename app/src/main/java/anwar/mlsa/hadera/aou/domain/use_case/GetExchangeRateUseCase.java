package anwar.mlsa.hadera.aou.domain.use_case;

import java.util.function.Consumer;

import anwar.mlsa.hadera.aou.domain.repository.TransactionRepository;
import anwar.mlsa.hadera.aou.domain.util.Result;

public class GetExchangeRateUseCase {

    private final TransactionRepository repository;

    public GetExchangeRateUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public void execute(Consumer<Result<String>> callback) {
        repository.getExchangeRate(callback);
    }
}
