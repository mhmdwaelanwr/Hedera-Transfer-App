package anwar.mlsa.hadera.aou;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class BlogApiParser {

    public static ArrayList<TransferActivity.Post> parse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.getBoolean("success")) {
                JSONArray postsArray = jsonResponse.getJSONArray("posts");
                return new Gson().fromJson(postsArray.toString(), new TypeToken<ArrayList<TransferActivity.Post>>() {}.getType());
            }
        } catch (JSONException e) {
            // Log the error
        }
        return new ArrayList<>();
    }
}
