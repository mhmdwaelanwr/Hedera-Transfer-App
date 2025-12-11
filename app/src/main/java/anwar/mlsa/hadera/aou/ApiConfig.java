package anwar.mlsa.hadera.aou;

import java.util.HashMap;

public class ApiConfig {

    public static final String BASE_URL = "https://mlsa-hedera-transfer-api.vercel.app";
    
    public static final String VERIFY_ENDPOINT = "/account/verify";
    public static final String BALANCE_ENDPOINT = "/account/balance/{accountId}";
    public static final String TRANSACTION_ENDPOINT = "/account/transaction";
    public static final String HISTORY_ENDPOINT = "/account/history/{accountId}";

    public static HashMap<String, Object> getVerificationBody(String accountId, String privateKey) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("accountId", accountId);
        map.put("privateKey", privateKey);
        return map;
    }

    public static String getBalanceUrl(String accountId) {
        return BASE_URL + BALANCE_ENDPOINT.replace("{accountId}", accountId);
    }
    
    public static HashMap<String, Object> getTransactionBody(String accountId, String privateKey, double amount, String receiverAccountId, String memo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("accountId", accountId);
        map.put("privateKey", privateKey);
        map.put("amount", amount);
        map.put("receiverAccountId", receiverAccountId);
        map.put("memo", memo);
        return map;
    }

    public static String getHistoryUrl(String accountId) {
        return BASE_URL + HISTORY_ENDPOINT.replace("{accountId}", accountId);
    }
}
