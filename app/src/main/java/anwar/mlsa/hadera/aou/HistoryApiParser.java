package anwar.mlsa.hadera.aou;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;

public class HistoryApiParser {

    public static class HistoryResponse {
        public ArrayList<Transaction> transactions;
        public String nextUrl;

        HistoryResponse(ArrayList<Transaction> transactions, String nextUrl) {
            this.transactions = transactions;
            this.nextUrl = nextUrl;
        }
    }

    public static HistoryResponse parse(String response, String currentAccountId) {
        ArrayList<Transaction> newTransactions = new ArrayList<>();
        String nextUrl = null;

        if (response == null) {
            return new HistoryResponse(newTransactions, null);
        }

        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject links = jsonResponse.optJSONObject("links");
            nextUrl = (links != null) ? links.optString("next", null) : null;

            JSONArray transactions = jsonResponse.getJSONArray("transactions");
            for (int i = 0; i < transactions.length(); i++) {
                JSONObject tx = transactions.getJSONObject(i);

                if (!"CRYPTOTRANSFER".equals(tx.optString("name"))) {
                    continue;
                }

                long userAmount = 0;
                boolean userFound = false;
                JSONArray transfers = tx.getJSONArray("transfers");

                int userAccountTransfers = 0;
                for (int j = 0; j < transfers.length(); j++) {
                    if (transfers.getJSONObject(j).getString("account").length() > 8) {
                        userAccountTransfers++;
                    }
                }
                if (userAccountTransfers < 2) {
                    continue;
                }

                for (int j = 0; j < transfers.length(); j++) {
                    if (currentAccountId.equals(transfers.getJSONObject(j).getString("account"))) {
                        userAmount = transfers.getJSONObject(j).getLong("amount");
                        userFound = true;
                        break;
                    }
                }

                if (!userFound) continue;

                Transaction transaction = new Transaction();
                transaction.transactionId = tx.getString("consensus_timestamp");
                long chargedTxFee = tx.getLong("charged_tx_fee");
                String otherPartyAccount = "";

                if (userAmount < 0) {
                    transaction.type = "Sent";
                    long principalRecipientAmount = 0;
                    for (int j = 0; j < transfers.length(); j++) {
                        JSONObject t = transfers.getJSONObject(j);
                        if (t.getLong("amount") > principalRecipientAmount) {
                            principalRecipientAmount = t.getLong("amount");
                            otherPartyAccount = t.getString("account");
                        }
                    }
                    transaction.amount = String.format(Locale.US, "-%.8f ℏ", principalRecipientAmount / 100_000_000.0);
                    transaction.fee = String.format(Locale.US, "%.8f ℏ", chargedTxFee / 100_000_000.0);
                    transaction.party = otherPartyAccount;
                } else { 
                    transaction.type = "Received";
                    long principalSenderAmount = 0;
                    for (int j = 0; j < transfers.length(); j++) {
                        JSONObject t = transfers.getJSONObject(j);
                        if (t.getLong("amount") < principalSenderAmount) {
                            principalSenderAmount = t.getLong("amount");
                            otherPartyAccount = t.getString("account");
                        }
                    }
                    transaction.amount = String.format(Locale.US, "+%.8f ℏ", userAmount / 100_000_000.0);
                    transaction.fee = null;
                    transaction.party = otherPartyAccount;
                }
                transaction.date = formatHederaTimestamp(tx.getString("consensus_timestamp"));
                transaction.status = tx.getString("result");
                String memoBase64 = tx.optString("memo_base64", null);
                if (memoBase64 != null && !memoBase64.isEmpty()) {
                    try {
                        transaction.memo = new String(Base64.getDecoder().decode(memoBase64));
                    } catch (IllegalArgumentException e) {
                        transaction.memo = "";
                    }
                } else {
                    transaction.memo = "";
                }

                newTransactions.add(transaction);
            }
        } catch (JSONException e) {
        }
        return new HistoryResponse(newTransactions, nextUrl);
    }

    private static String formatHederaTimestamp(String consensusTimestamp) {
        try {
            String[] parts = consensusTimestamp.split("\\.");
            long seconds = Long.parseLong(parts[0]);
            int nanos = 0;
            if (parts.length > 1) {
                String nanosString = parts[1];
                if (nanosString.length() < 9) {
                    nanosString = String.format("%-9s", nanosString).replace(' ', '0');
                }
                nanos = Integer.parseInt(nanosString.substring(0, 9));
            }
            Instant instant = Instant.ofEpochSecond(seconds, nanos);
            LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return dateTime.format(formatter);
        } catch (Exception e) {
            return consensusTimestamp;
        }
    }
}
