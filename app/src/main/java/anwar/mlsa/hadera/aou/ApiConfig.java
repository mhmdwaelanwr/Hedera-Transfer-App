package anwar.mlsa.hadera.aou;

import java.util.HashMap;

public class ApiConfig {

    // URL الأساسي للخادم
    public static final String BASE_URL = "https://mlsa-hedera-transfer-api.vercel.app";
    
    // نقاط النهاية لعمليات الـ API
    public static final String VERIFY_ENDPOINT = "/account/verify";        // POST: للتحقق من بيانات الدخول
    public static final String BALANCE_ENDPOINT = "/account/balance/{accountId}"; // GET: للحصول على الرصيد
    public static final String TRANSACTION_ENDPOINT = "/account/transaction"; // POST: لإجراء التحويل
    public static final String HISTORY_ENDPOINT = "/account/history/{accountId}"; // GET: للحصول على سجل المعاملات

    // -----------------------------------------------------------------
    // 1. Verification Logic (MainActivity)
    // -----------------------------------------------------------------
    
    /**
     * إنشاء جسم الطلب (Body) الخاص بالتحقق من بيانات الدخول.
     */
    public static HashMap<String, Object> getVerificationBody(String accountId, String privateKey) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("accountId", accountId);
        map.put("privateKey", privateKey);
        return map;
    }

    // -----------------------------------------------------------------
    // 2. Balance Logic (TransferActivity)
    // -----------------------------------------------------------------
    
    /**
     * بناء رابط GET لطلب الرصيد باستخدام الـ Account ID.
     * مثال: BASE_URL/account/balance/0.0.123
     */
    public static String getBalanceUrl(String accountId) {
        return BASE_URL + BALANCE_ENDPOINT.replace("{accountId}", accountId);
    }
    
    // -----------------------------------------------------------------
    // 3. Transaction Logic (TransferActivity)
    // -----------------------------------------------------------------

    /**
     * إنشاء جسم الطلب (Body) الخاص بإجراء التحويل.
     */
    public static HashMap<String, Object> getTransactionBody(String accountId, String privateKey, double amount, String receiverAccountId, String memo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("accountId", accountId);
        map.put("privateKey", privateKey);
        map.put("amount", amount);
        map.put("receiverAccountId", receiverAccountId);
        map.put("memo", memo);
        return map;
    }

    // -----------------------------------------------------------------
    // 4. History Logic (TransferActivity)
    // -----------------------------------------------------------------

    /**
     * بناء رابط GET لطلب سجل المعاملات باستخدام الـ Account ID.
     * مثال: BASE_URL/account/history/0.0.123
     */
    public static String getHistoryUrl(String accountId) {
        return BASE_URL + HISTORY_ENDPOINT.replace("{accountId}", accountId);
    }
}