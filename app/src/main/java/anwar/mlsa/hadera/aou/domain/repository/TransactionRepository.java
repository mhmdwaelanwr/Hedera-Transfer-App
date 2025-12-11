package anwar.mlsa.hadera.aou.domain.repository;

import java.util.Map;
import java.util.function.Consumer;

import anwar.mlsa.hadera.aou.HistoryApiParser;
import anwar.mlsa.hadera.aou.domain.util.Result;

public interface TransactionRepository {

    void verifyAccount(String accountId, Consumer<Result<Boolean>> callback);

    void sendTransaction(
        String recipientId,
        double amount,
        String memo,
        Consumer<Result<Map<String, Object>>> callback
    );

    void getBalance(String accountId, Consumer<Result<Map<String, Object>>> callback);

    void getHistory(String accountId, String url, Consumer<Result<HistoryApiParser.HistoryResponse>> callback);
}
