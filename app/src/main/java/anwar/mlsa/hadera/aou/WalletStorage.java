package anwar.mlsa.hadera.aou;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WalletStorage {

    private static final String PREF_NAME = "EncryptedWalletData";
    private static final String KEY_ACCOUNTS = "ACCOUNTS";
    private static final String KEY_CURRENT_ACCOUNT_INDEX = "CURRENT_ACCOUNT_INDEX";
    private static final int MAX_ACCOUNTS = 6;

    private static final String SUFFIX_FORMATTED_BALANCE = "_FORMATTED_BALANCE";
    private static final String SUFFIX_RAW_BALANCE = "_RAW_BALANCE";
    private static final String SUFFIX_TRANSACTION_HISTORY = "_TRANSACTION_HISTORY";
    private static final String SUFFIX_HISTORY_MIGRATED = "_HISTORY_MIGRATED";

    private static SharedPreferences getPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e("WalletStorage", "Could not create encrypted shared preferences", e);
            throw new RuntimeException("Could not create encrypted shared preferences", e);
        }
    }

    public static List<Account> getAccounts(Context context) {
        String json = getPrefs(context).getString(KEY_ACCOUNTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Account>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void saveAccounts(Context context, List<Account> accounts) {
        getPrefs(context).edit().putString(KEY_ACCOUNTS, new Gson().toJson(accounts)).apply();
    }

    public static boolean canAddAccount(Context context) {
        return getAccounts(context).size() < MAX_ACCOUNTS;
    }

    public static boolean addAccount(Context context, String accountId, String privateKey) {
        List<Account> accounts = getAccounts(context);
        if (accounts.size() >= MAX_ACCOUNTS) return false;
        for (Account account : accounts) {
            if (account.getAccountId().equals(accountId)) return false;
        }
        accounts.add(new Account(accountId, privateKey));
        saveAccounts(context, accounts);
        if (accounts.size() == 1) setCurrentAccountIndex(context, 0);
        return true;
    }

    public static void deleteAccount(Context context, int index) {
        List<Account> accounts = getAccounts(context);
        if (index >= 0 && index < accounts.size()) {
            String accountId = accounts.get(index).getAccountId();
            SharedPreferences.Editor editor = getPrefs(context).edit();
            editor.remove(accountId + SUFFIX_FORMATTED_BALANCE);
            editor.remove(accountId + SUFFIX_RAW_BALANCE);
            editor.remove(accountId + SUFFIX_TRANSACTION_HISTORY);
            editor.remove(accountId + SUFFIX_HISTORY_MIGRATED);
            accounts.remove(index);
            saveAccounts(context, accounts);

            int currentIdx = getCurrentAccountIndex(context);
            if (currentIdx == index) {
                setCurrentAccountIndex(context, accounts.isEmpty() ? -1 : 0);
            } else if (currentIdx > index) {
                setCurrentAccountIndex(context, currentIdx - 1);
            }
        }
    }

    public static void setCurrentAccountIndex(Context context, int index) {
        getPrefs(context).edit().putInt(KEY_CURRENT_ACCOUNT_INDEX, index).apply();
    }

    public static int getCurrentAccountIndex(Context context) {
        return getPrefs(context).getInt(KEY_CURRENT_ACCOUNT_INDEX, -1);
    }

    public static Account getCurrentAccount(Context context) {
        int index = getCurrentAccountIndex(context);
        if (index != -1) {
            List<Account> accounts = getAccounts(context);
            if (index < accounts.size()) return accounts.get(index);
        }
        return null;
    }

    public static boolean isWalletSaved(Context context) {
        return getCurrentAccount(context) != null;
    }

    public static String getAccountId(Context context) {
        Account currentAccount = getCurrentAccount(context);
        return (currentAccount != null) ? currentAccount.getAccountId() : null;
    }

    public static String getPrivateKey(Context context) {
        Account currentAccount = getCurrentAccount(context);
        return (currentAccount != null) ? currentAccount.getPrivateKey() : null;
    }

    public static void saveFormattedBalance(Context context, String formattedBalance) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            getPrefs(context).edit().putString(accountId + SUFFIX_FORMATTED_BALANCE, formattedBalance).apply();
        }
    }

    public static String getFormattedBalance(Context context) {
        String accountId = getAccountId(context);
        return (accountId != null) ? getPrefs(context).getString(accountId + SUFFIX_FORMATTED_BALANCE, "0.00 ℏ") : "0.00 ℏ";
    }

    public static void saveRawBalance(Context context, double rawBalance) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            getPrefs(context).edit().putLong(accountId + SUFFIX_RAW_BALANCE, Double.doubleToRawLongBits(rawBalance)).apply();
        }
    }

    public static double getRawBalance(Context context) {
        String accountId = getAccountId(context);
        return (accountId != null) ? Double.longBitsToDouble(getPrefs(context).getLong(accountId + SUFFIX_RAW_BALANCE, 0L)) : 0.0;
    }

    private static void migrateHistoryIfNecessary(Context context, String accountId) {
        SharedPreferences prefs = getPrefs(context);
        boolean isMigrated = prefs.getBoolean(accountId + SUFFIX_HISTORY_MIGRATED, false);
        if (isMigrated) {
            return;
        }

        String json = prefs.getString(accountId + SUFFIX_TRANSACTION_HISTORY, null);
        if (json == null || json.isEmpty() || json.equals("[]")) {
            prefs.edit().putBoolean(accountId + SUFFIX_HISTORY_MIGRATED, true).apply();
            return; 
        }

        Gson gson = new Gson();
        try {
            Type oldHistoryType = new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType();
            List<Map<String, Object>> oldHistory = gson.fromJson(json, oldHistoryType);

            if (oldHistory != null && !oldHistory.isEmpty() && oldHistory.get(0) != null && oldHistory.get(0).containsKey("recipient")) {
                ArrayList<Transaction> newHistory = new ArrayList<>();
                for (Map<String, Object> oldTx : oldHistory) {
                    Transaction newTx = new Transaction();
                    newTx.type = (String) oldTx.get("type");
                    newTx.date = (String) oldTx.get("date");
                    newTx.amount = (String) oldTx.get("amount");
                    newTx.party = "To: " + oldTx.get("recipient");
                    newTx.status = (String) oldTx.get("status");
                    newTx.fee = oldTx.containsKey("fee") ? (String) oldTx.get("fee") : "";
                    newTx.memo = oldTx.containsKey("memo") ? (String) oldTx.get("memo") : "";
                    newHistory.add(newTx);
                }

                String newJson = gson.toJson(newHistory);
                prefs.edit().putString(accountId + SUFFIX_TRANSACTION_HISTORY, newJson).apply();
                Log.i("WalletStorage", "Successfully migrated transaction history for account " + accountId);
            }
        } catch (Exception e) {
            Log.e("WalletStorage", "Could not migrate history, data might already be new or is corrupt.", e);
        } finally {
            prefs.edit().putBoolean(accountId + SUFFIX_HISTORY_MIGRATED, true).apply();
        }
    }

    public static ArrayList<Transaction> getHistory(Context context) {
        String accountId = getAccountId(context);
        if (accountId == null) {
            return new ArrayList<>();
        }

        migrateHistoryIfNecessary(context, accountId);

        String json = getPrefs(context).getString(accountId + SUFFIX_TRANSACTION_HISTORY, null);

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type transactionListType = new TypeToken<ArrayList<Transaction>>() {}.getType();
            ArrayList<Transaction> history = new Gson().fromJson(json, transactionListType);
            return history != null ? history : new ArrayList<>();
        } catch (JsonSyntaxException e) {
            Log.e("WalletStorage", "Could not parse migrated history, returning empty list.", e);
            return new ArrayList<>();
        }
    }

    public static void saveTransaction(Context context, Transaction newTransaction) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            ArrayList<Transaction> history = getHistory(context);
            if (history == null) { 
                history = new ArrayList<>();
            }
            history.add(0, newTransaction);
            String json = new Gson().toJson(history);
            getPrefs(context).edit().putString(accountId + SUFFIX_TRANSACTION_HISTORY, json).apply();
        }
    }

    public static void logout(Context context) {
        getPrefs(context).edit().clear().apply();
    }

    public static class Account {
        private String accountId;
        private String privateKey;

        public Account(String accountId, String privateKey) {
            this.accountId = accountId;
            this.privateKey = privateKey;
        }

        public String getAccountId() {
            return accountId;
        }

        public String getPrivateKey() {
            return privateKey;
        }
    }
}
