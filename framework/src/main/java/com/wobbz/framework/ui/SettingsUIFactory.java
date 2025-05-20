package com.wobbz.framework.ui;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Factory class for dynamically generating UI components based on module settings JSON schemas.
 * Handles the creation of appropriate UI elements for different setting types and manages
 * their values and interactions.
 */
public class SettingsUIFactory {

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_ARRAY = "array";
    
    private static final String PROP_TYPE = "type";
    private static final String PROP_PROPERTIES = "properties";
    private static final String PROP_TITLE = "title";
    private static final String PROP_DESCRIPTION = "description";
    private static final String PROP_DEFAULT = "default";
    private static final String PROP_MINIMUM = "minimum";
    private static final String PROP_MAXIMUM = "maximum";
    private static final String PROP_OPTIONS = "enum";
    private static final String PROP_ITEMS = "items";
    
    /**
     * Creates a UI layout from a JSON settings schema file.
     *
     * @param context The Android context
     * @param jsonFilePath Path to the JSON schema file
     * @return A View containing the generated UI
     */
    public static View createFromJson(Context context, String jsonFilePath) {
        try {
            // Read the JSON file
            File file = new File(jsonFilePath);
            StringBuilder jsonContent = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line).append("\n");
                }
            }
            
            // Parse the JSON
            JSONObject jsonSchema = new JSONObject(jsonContent.toString());
            
            // Create the UI
            return createFromJsonObject(context, jsonSchema);
            
        } catch (IOException | JSONException e) {
            // Create an error view
            TextView errorView = new TextView(context);
            errorView.setText("Error loading settings: " + e.getMessage());
            return errorView;
        }
    }
    
    /**
     * Creates a UI layout from a JSON settings schema object.
     *
     * @param context The Android context
     * @param jsonSchema The JSON schema object
     * @return A View containing the generated UI
     */
    public static View createFromJsonObject(Context context, JSONObject jsonSchema) {
        try {
            String type = jsonSchema.optString(PROP_TYPE, TYPE_OBJECT);
            
            if (TYPE_OBJECT.equals(type) && jsonSchema.has(PROP_PROPERTIES)) {
                return createObjectUI(context, jsonSchema);
            } else {
                // For non-object schemas or schemas without properties
                TextView errorView = new TextView(context);
                errorView.setText("Invalid schema format: root must be an object with properties");
                return errorView;
            }
            
        } catch (JSONException e) {
            TextView errorView = new TextView(context);
            errorView.setText("Error parsing settings: " + e.getMessage());
            return errorView;
        }
    }
    
    /**
     * Creates UI for an object type schema with properties.
     *
     * @param context The Android context
     * @param jsonSchema The JSON schema object
     * @return A LinearLayout containing UI elements for each property
     * @throws JSONException If JSON parsing fails
     */
    private static LinearLayout createObjectUI(Context context, JSONObject jsonSchema) throws JSONException {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        
        JSONObject properties = jsonSchema.getJSONObject(PROP_PROPERTIES);
        Iterator<String> keys = properties.keys();
        
        // Map to store the created views keyed by property name (for later value retrieval)
        Map<String, View> propertyViews = new HashMap<>();
        
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject property = properties.getJSONObject(key);
            String type = property.optString(PROP_TYPE);
            
            // Create a container for this property
            LinearLayout propertyLayout = new LinearLayout(context);
            propertyLayout.setOrientation(LinearLayout.VERTICAL);
            propertyLayout.setPadding(0, 8, 0, 8);
            
            // Add the title/label
            TextView labelView = new TextView(context);
            labelView.setText(property.optString(PROP_TITLE, key));
            labelView.setTextSize(16);
            propertyLayout.addView(labelView);
            
            // Add the description if present
            if (property.has(PROP_DESCRIPTION)) {
                TextView descView = new TextView(context);
                descView.setText(property.getString(PROP_DESCRIPTION));
                descView.setTextSize(12);
                propertyLayout.addView(descView);
            }
            
            // Create the appropriate control based on the property type
            View controlView = null;
            
            switch (type) {
                case TYPE_BOOLEAN:
                    controlView = createBooleanControl(context, property);
                    break;
                case TYPE_INTEGER:
                case TYPE_NUMBER:
                    controlView = createNumericControl(context, property);
                    break;
                case TYPE_STRING:
                    controlView = createStringControl(context, property);
                    break;
                case TYPE_ARRAY:
                    controlView = createArrayControl(context, property);
                    break;
                default:
                    // Unsupported type
                    TextView unsupportedView = new TextView(context);
                    unsupportedView.setText("Unsupported property type: " + type);
                    controlView = unsupportedView;
                    break;
            }
            
            if (controlView != null) {
                propertyLayout.addView(controlView);
                propertyViews.put(key, controlView);
            }
            
            layout.addView(propertyLayout);
        }
        
        // Attach the property views map to the layout for later retrieval
        layout.setTag(propertyViews);
        
        return layout;
    }
    
    /**
     * Creates a control for boolean properties (typically a checkbox).
     *
     * @param context The Android context
     * @param property The JSON property object
     * @return A CheckBox view
     */
    private static View createBooleanControl(Context context, JSONObject property) {
        CheckBox checkBox = new CheckBox(context);
        
        // Set default value if present
        if (property.has(PROP_DEFAULT)) {
            try {
                checkBox.setChecked(property.getBoolean(PROP_DEFAULT));
            } catch (JSONException e) {
                // Ignore, use default false
            }
        }
        
        return checkBox;
    }
    
    /**
     * Creates a control for numeric properties (integer or number).
     * Uses a SeekBar for values with min/max, EditText otherwise.
     *
     * @param context The Android context
     * @param property The JSON property object
     * @return A View for numeric input
     */
    private static View createNumericControl(Context context, JSONObject property) {
        // Check if min and max are specified
        if (property.has(PROP_MINIMUM) && property.has(PROP_MAXIMUM)) {
            try {
                int min = property.getInt(PROP_MINIMUM);
                int max = property.getInt(PROP_MAXIMUM);
                int defaultValue = property.optInt(PROP_DEFAULT, min);
                
                // Create a seek bar with text display
                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                
                // Value display
                TextView valueDisplay = new TextView(context);
                valueDisplay.setText(String.valueOf(defaultValue));
                container.addView(valueDisplay);
                
                // Seek bar
                SeekBar seekBar = new SeekBar(context);
                seekBar.setMax(max - min);
                seekBar.setProgress(defaultValue - min);
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int actualValue = progress + min;
                        valueDisplay.setText(String.valueOf(actualValue));
                    }
                    
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }
                    
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                container.addView(seekBar);
                
                return container;
            } catch (JSONException e) {
                // Fall back to text input if there's an error
            }
        }
        
        // Default to an EditText for numeric input
        EditText editText = new EditText(context);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        // Set default value if present
        if (property.has(PROP_DEFAULT)) {
            try {
                String defaultValue = property.get(PROP_DEFAULT).toString();
                editText.setText(defaultValue);
            } catch (JSONException e) {
                // Ignore
            }
        }
        
        return editText;
    }
    
    /**
     * Creates a control for string properties.
     *
     * @param context The Android context
     * @param property The JSON property object
     * @return An EditText view
     */
    private static View createStringControl(Context context, JSONObject property) {
        // Check if enum/options are specified
        if (property.has(PROP_OPTIONS)) {
            try {
                JSONArray options = property.getJSONArray(PROP_OPTIONS);
                // Create a dropdown/spinner for the options
                // For simplicity in this example, we'll just use an EditText with a note
                EditText editText = new EditText(context);
                editText.setHint("Choose from: " + options.toString());
                
                // Set default if present
                if (property.has(PROP_DEFAULT)) {
                    editText.setText(property.getString(PROP_DEFAULT));
                }
                
                return editText;
                
                // In a real implementation, you'd create a Spinner (dropdown)
                // with these options instead
            } catch (JSONException e) {
                // Fall back to standard text input
            }
        }
        
        // Standard text input
        EditText editText = new EditText(context);
        
        // Set default value if present
        if (property.has(PROP_DEFAULT)) {
            try {
                editText.setText(property.getString(PROP_DEFAULT));
            } catch (JSONException e) {
                // Ignore
            }
        }
        
        return editText;
    }
    
    /**
     * Creates a control for array properties.
     * Note: This is a simplified implementation that just shows a text representation.
     * A full implementation would use a RecyclerView or similar.
     *
     * @param context The Android context
     * @param property The JSON property object
     * @return A TextView showing the array structure
     */
    private static View createArrayControl(Context context, JSONObject property) {
        TextView textView = new TextView(context);
        textView.setText("Array properties are not fully supported in this UI. Please edit the JSON directly.");
        
        return textView;
    }
    
    /**
     * Gets the current values from a generated settings UI as a JSONObject.
     *
     * @param settingsView The root view created by createFromJson
     * @return A JSONObject containing the current UI values
     */
    public static JSONObject getValuesFromUI(View settingsView) {
        if (!(settingsView instanceof LinearLayout) || settingsView.getTag() == null) {
            return new JSONObject();
        }
        
        try {
            JSONObject result = new JSONObject();
            Map<String, View> propertyViews = (Map<String, View>) settingsView.getTag();
            
            for (Map.Entry<String, View> entry : propertyViews.entrySet()) {
                String key = entry.getKey();
                View view = entry.getValue();
                
                if (view instanceof CheckBox) {
                    result.put(key, ((CheckBox) view).isChecked());
                } else if (view instanceof EditText) {
                    String text = ((EditText) view).getText().toString();
                    
                    // Try to parse as a number if it looks like one
                    if (text.matches("-?\\d+")) {
                        result.put(key, Integer.parseInt(text));
                    } else if (text.matches("-?\\d+\\.\\d+")) {
                        result.put(key, Double.parseDouble(text));
                    } else {
                        result.put(key, text);
                    }
                } else if (view instanceof LinearLayout && ((LinearLayout) view).getChildCount() > 1) {
                    // This might be our numeric slider (SeekBar + TextView)
                    View child = ((LinearLayout) view).getChildAt(1);
                    if (child instanceof SeekBar) {
                        SeekBar seekBar = (SeekBar) child;
                        result.put(key, seekBar.getProgress());
                    }
                }
            }
            
            return result;
        } catch (JSONException | ClassCastException e) {
            return new JSONObject();
        }
    }
} 