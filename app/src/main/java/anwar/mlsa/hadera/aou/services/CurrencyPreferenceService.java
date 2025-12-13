package anwar.mlsa.hadera.aou.services;

import android.content.Context;
import android.content.SharedPreferences;

public class CurrencyPreferenceService {
    private static final String PREFS_NAME = "CurrencyPrefs";
    private static final String KEY_SELECTED_CURRENCY = "SELECTED_CURRENCY";
    private static final String DEFAULT_CURRENCY = "usd";

    public static final String[] SUPPORTED_CURRENCIES = {"usd", "eur", "egp", "gbp", "jpy", "cad", "aud"};
    public static final String[] CURRENCY_NAMES = {"USD", "EUR", "EGP", "GBP", "JPY", "CAD", "AUD"};
    public static final String[] CURRENCY_SYMBOLS = {"$", "€", "E£", "£", "¥", "C$", "A$"};

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getSelectedCurrency(Context context) {
        return getPrefs(context).getString(KEY_SELECTED_CURRENCY, DEFAULT_CURRENCY);
    }

    public static void setSelectedCurrency(Context context, String currency) {
        getPrefs(context).edit().putString(KEY_SELECTED_CURRENCY, currency.toLowerCase()).apply();
    }

    public static String getCurrencySymbol(Context context) {
        String currency = getSelectedCurrency(context);
        for (int i = 0; i < SUPPORTED_CURRENCIES.length; i++) {
            if (SUPPORTED_CURRENCIES[i].equals(currency)) {
                return CURRENCY_SYMBOLS[i];
            }
        }
        return "$";
    }

    public static String getCurrencyName(Context context) {
        String currency = getSelectedCurrency(context);
        for (int i = 0; i < SUPPORTED_CURRENCIES.length; i++) {
            if (SUPPORTED_CURRENCIES[i].equals(currency)) {
                return CURRENCY_NAMES[i];
            }
        }
        return "USD";
    }
}
