package anwar.mlsa.hadera.aou.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import anwar.mlsa.hadera.aou.models.AddressBookEntry;

public class AddressBookService {
    private static final String PREFS_NAME = "AddressBookPrefs";
    private static final String KEY_ADDRESS_BOOK = "ADDRESS_BOOK";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<AddressBookEntry> getAddressBook(Context context) {
        String json = getPrefs(context).getString(KEY_ADDRESS_BOOK, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<AddressBookEntry>>() {}.getType();
        List<AddressBookEntry> entries = new Gson().fromJson(json, type);
        return entries != null ? entries : new ArrayList<>();
    }

    public static void saveAddressBook(Context context, List<AddressBookEntry> entries) {
        getPrefs(context).edit()
                .putString(KEY_ADDRESS_BOOK, new Gson().toJson(entries))
                .apply();
    }

    public static boolean addEntry(Context context, String label, String accountId, String memo) {
        List<AddressBookEntry> entries = getAddressBook(context);
        
        // Check if account ID already exists
        for (AddressBookEntry entry : entries) {
            if (entry.getAccountId().equals(accountId)) {
                return false;
            }
        }
        
        String id = UUID.randomUUID().toString();
        entries.add(new AddressBookEntry(id, label, accountId, memo));
        saveAddressBook(context, entries);
        return true;
    }

    public static boolean updateEntry(Context context, String id, String label, String accountId, String memo) {
        List<AddressBookEntry> entries = getAddressBook(context);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(id)) {
                entries.set(i, new AddressBookEntry(id, label, accountId, memo));
                saveAddressBook(context, entries);
                return true;
            }
        }
        return false;
    }

    public static boolean deleteEntry(Context context, String id) {
        List<AddressBookEntry> entries = getAddressBook(context);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(id)) {
                entries.remove(i);
                saveAddressBook(context, entries);
                return true;
            }
        }
        return false;
    }

    public static AddressBookEntry getEntryByAccountId(Context context, String accountId) {
        List<AddressBookEntry> entries = getAddressBook(context);
        for (AddressBookEntry entry : entries) {
            if (entry.getAccountId().equals(accountId)) {
                return entry;
            }
        }
        return null;
    }
}
