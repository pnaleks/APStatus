package pnapp.tools.apstatus;

import android.content.Intent;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class IntentGsonAdapter implements
        JsonSerializer<Intent>,
        JsonDeserializer<Intent>
{
    @Override
    public Intent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return null;
    }

    @Override
    public JsonElement serialize(Intent src, Type typeOfSrc, JsonSerializationContext context) {


        return null;
    }
}
