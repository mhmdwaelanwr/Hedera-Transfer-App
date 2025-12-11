package anwar.mlsa.hadera.aou.domain.use_case;

import java.util.function.Consumer;

import anwar.mlsa.hadera.aou.domain.repository.TransactionRepository;
import anwar.mlsa.hadera.aou.domain.util.Result;

public class VerifyAccountUseCase {

    private static final String ACCOUNT_ID_REGEX = "^0\\.0\\.[0-9]+$";
    private final TransactionRepository repository;

    public VerifyAccountUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public void execute(String accountId, Consumer<Result<Boolean>> callback) {
        if (accountId == null || !accountId.matches(ACCOUNT_ID_REGEX)) {
            callback.accept(new Result.Error<>("Invalid Account ID format."));
            return;
        }
        repository.verifyAccount(accountId, callback);
    }
}
