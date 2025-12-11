package anwar.mlsa.hadera.aou.domain.use_case;

import java.util.Map;
import java.util.function.Consumer;

import anwar.mlsa.hadera.aou.domain.repository.TransactionRepository;
import anwar.mlsa.hadera.aou.domain.util.Result;

public class SendTransactionUseCase {

    private final TransactionRepository repository;

    public SendTransactionUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public void execute(String recipientId, String amountStr, String memo, double currentBalance, Consumer<Result<Map<String, Object>>> callback) {
        Result<Double> validationResult = validateAmount(amountStr, currentBalance);
        if (validationResult instanceof Result.Error) {
            callback.accept(new Result.Error<>(((Result.Error<Double>) validationResult).message));
            return;
        }

        Double amount = ((Result.Success<Double>) validationResult).data;
        repository.sendTransaction(recipientId, amount, memo, callback);
    }

    private Result<Double> validateAmount(String amountStr, double currentBalance) {
        if (amountStr == null || amountStr.isEmpty()) {
            return new Result.Error<>("Amount is required.");
        }
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                return new Result.Error<>("Amount must be positive.");
            } else if (amount > currentBalance) {
                return new Result.Error<>("Amount exceeds balance.");
            }
            return new Result.Success<>(amount);
        } catch (NumberFormatException e) {
            return new Result.Error<>("Invalid amount format.");
        }
    }
}
