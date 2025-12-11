package anwar.mlsa.hadera.aou.data.repository;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import anwar.mlsa.hadera.aou.ApiConfig;
import anwar.mlsa.hadera.aou.HistoryApiParser;
import anwar.mlsa.hadera.aou.RequestNetwork;
import anwar.mlsa.hadera.aou.RequestNetworkController;
import anwar.mlsa.hadera.aou.WalletStorage;
import anwar.mlsa.hadera.aou.domain.repository.TransactionRepository;
import anwar.mlsa.hadera.aou.domain.util.Result;

public class TransactionRepositoryImpl implements TransactionRepository {

    private static final String VERIFY_TAG = "VERIFY_ACCOUNT";
    private static final String SEND_TX_TAG = "SEND_TRANSACTION";
    private static final String BALANCE_TAG = "GET_BALANCE";
    private static final String HISTORY_TAG = "GET_HISTORY";
    private static final String HEDERA_API_BASE_URL = "https://testnet.mirrornode.hedera.com";

    private final RequestNetwork networkReq;
    private final Context context;
    private final Gson gson = new Gson();

    public TransactionRepositoryImpl(Context context) {
        this.context = context.getApplicationContext();
        this.networkReq = new RequestNetwork(this.context);
    }

    @Override
    public void verifyAccount(String accountId, Consumer<Result<Boolean>> callback) {
        callback.accept(new Result.Loading<>());
        String url = HEDERA_API_BASE_URL + "/api/v1/balances?account.id=" + accountId;
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, VERIFY_TAG, new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    Map<String, Object> map = gson.fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
                    callback.accept(new Result.Success<>(map.containsKey("balances")));
                } catch (Exception e) {
                    callback.accept(new Result.Error<>("Failed to parse verification response."));
                }
            }
            @Override
            public void onErrorResponse(String tag, String message) {
                callback.accept(new Result.Error<>(message));
            }
        });
    }

    @Override
    public void sendTransaction(String recipientId, double amount, String memo, Consumer<Result<Map<String, Object>>> callback) {
        callback.accept(new Result.Loading<>());
        String senderAccountId = WalletStorage.getAccountId(context);
        String senderPrivateKey = WalletStorage.getPrivateKey(context);

        if (senderAccountId == null || senderPrivateKey == null) {
            callback.accept(new Result.Error<>("User credentials not found."));
            return;
        }

        HashMap<String, Object> body = ApiConfig.getTransactionBody(senderAccountId, senderPrivateKey, amount, recipientId, memo);
        networkReq.setParams(body, RequestNetworkController.REQUEST_BODY);
        networkReq.startRequestNetwork(RequestNetworkController.POST, ApiConfig.BASE_URL + ApiConfig.TRANSACTION_ENDPOINT, SEND_TX_TAG, new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    Map<String, Object> map = gson.fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
                    if (map != null && "Transfer Success".equals(map.get("status"))) {
                        callback.accept(new Result.Success<>(map));
                    } else {
                        String error = (map != null && map.get("error") != null) ? map.get("error").toString() : "Unknown transaction error";
                        callback.accept(new Result.Error<>(error));
                    }
                } catch (Exception e) {
                    callback.accept(new Result.Error<>("Failed to parse transaction response."));
                }
            }
            @Override
            public void onErrorResponse(String tag, String message) {
                callback.accept(new Result.Error<>(message));
            }
        });
    }

    @Override
    public void getBalance(String accountId, Consumer<Result<Map<String, Object>>> callback) {
        callback.accept(new Result.Loading<>());
        networkReq.startRequestNetwork(RequestNetworkController.GET, ApiConfig.getBalanceUrl(accountId), BALANCE_TAG, new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    Map<String, Object> map = gson.fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
                    callback.accept(new Result.Success<>(map));
                } catch (Exception e) {
                    callback.accept(new Result.Error<>("Failed to parse balance response."));
                }
            }
            @Override
            public void onErrorResponse(String tag, String message) {
                callback.accept(new Result.Error<>(message));
            }
        });
    }

    @Override
    public void getHistory(String accountId, String url, Consumer<Result<HistoryApiParser.HistoryResponse>> callback) {
        callback.accept(new Result.Loading<>());
        String requestUrl = (url != null) ? HEDERA_API_BASE_URL + url : HEDERA_API_BASE_URL + "/api/v1/transactions?account.id=" + accountId;
        networkReq.startRequestNetwork(RequestNetworkController.GET, requestUrl, HISTORY_TAG, new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                try {
                    HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, accountId);
                    callback.accept(new Result.Success<>(historyResponse));
                } catch (Exception e) {
                    callback.accept(new Result.Error<>("Failed to parse history response."));
                }
            }
            @Override
            public void onErrorResponse(String tag, String message) {
                callback.accept(new Result.Error<>(message));
            }
        });
    }
}
