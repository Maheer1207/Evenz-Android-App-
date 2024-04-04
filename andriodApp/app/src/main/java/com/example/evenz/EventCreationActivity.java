package com.example.evenz;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

import java.text.ParseException;
import java.util.UUID;


public class EventCreationActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 22;
    private Uri filePath;
    private ImageView imageView;
    private Button submitEventButton;
    private String eventPosterID_temp;
    private String eventID;
    private EditText editTextOrganizerName,editTextEventName, editDate, editTextAttendeeLimit, editTextEventInfo, editTextEventLoc;

    // ImageUtility instance
    private ImageUtility imageUtility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_event);

        // Initialize Firebase Storage reference
        StorageReference storageReference = FirebaseStorage.getInstance().getReference();

        // Initialize ImageUtility
        imageUtility = new ImageUtility();

        // Get extras from intent
        Bundle b = getIntent().getExtras();
        assert b != null;
        String role = b.getString("role");


        initUI();
        imageView = findViewById(R.id.vector_ek2);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select();
            }
        });

        // Submit event button listener for initiating the upload process
        submitEventButton.setOnClickListener(view -> {
            if (filePath != null) {
                ImageUtility.UploadCallback callback = new ImageUtility.UploadCallback() {
                    @Override
                    public void onSuccess(String imageID, String imageURL) throws ParseException {
                        // Image successfully uploaded
                        // Proceed with event submission or other actions as needed
                        eventPosterID_temp = imageID; // If needed for further operations
                        submitEvent();
                        navigateToHomeScreen();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // Handle upload failure
                        Toast.makeText(EventCreationActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                };

                imageUtility.upload(filePath, callback);
            } else {
                Toast.makeText(EventCreationActivity.this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Method to initiate the image selection
    private void select() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image from here..."), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initUI() {
        editTextOrganizerName = findViewById(R.id.editTextOrganizerName);
        editTextEventName = findViewById(R.id.editTextEventName);
        editDate = findViewById(R.id.editDate); // Ensure you have input formatting or parsing for date
        editTextAttendeeLimit = findViewById(R.id.no_limit);
        editTextEventInfo = findViewById(R.id.editTextEventInfo);
        editTextEventLoc = findViewById(R.id.editTextLocation);
        submitEventButton = findViewById(R.id.create_event_button); //Create event button
    }


    private void navigateToHomeScreen() {
        Intent intent = new Intent(EventCreationActivity.this, HomeScreenActivity.class);
        Bundle b = new Bundle();
        b.putString("role", "organizer");
        b.putString("eventID", eventID);
        intent.putExtras(b);
        startActivity(intent);
    }

    private void submitEvent() throws ParseException {

        String eventName = editTextEventName.getText().toString().trim();
        String eventPosterID = eventPosterID_temp; // Assuming a default or gathered elsewhere
        String description = editTextEventInfo.getText().toString().trim();
        String attendeeLimit = editTextAttendeeLimit.getText().toString().trim();
        String orgName = editTextOrganizerName.getText().toString().trim();
        String eventDatestring = editDate.getText().toString().trim();
        String location = editTextEventLoc.getText().toString().trim();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String deviceID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Reference to 'users' collection
        DocumentReference userDocRef = db.collection("users").document(deviceID);
        DocumentReference newEventRef = db.collection("events").document();

        eventID = newEventRef.getId(); // Use this eventID for your operations
        userDocRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() { //TODO: For MVP we are considering the eventList to be a string and not handling update if the user already exist; make ure you update that
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        // User exists, you can now proceed with event creation or update user data as needed
                    } else {
                        // User does not exist, create a new user with UserType set to "organizer"
                        Map<String, Object> newUser = new HashMap<>();
                        newUser.put("userType", "organizer");
                        // Add other user details as needed
                        newUser.put("userId", deviceID); // Assuming you want to use deviceID as userId
                        newUser.put("eventList", eventID);

                        // Save the new user
                        db.collection("users").document(deviceID).set(newUser);
                    }
                }
            }
        });

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
        Date eventDate = null;

        eventDate = dateFormat.parse(eventDatestring);

        // handle attendee limit error:
        int eventAttendeeLimit = 0; // Default to 0 or some other appropriate default value

        eventAttendeeLimit = Integer.parseInt(attendeeLimit);
        Geolocation geolocation = new Geolocation("asd", 0.0f, 0.0f); //TODO: replace with actual values
        Bitmap qrCodeBrowse = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888); // TODO: get random generated QR code
        Bitmap qrCodeCheckIn = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888); // TODO: get random generated QR code for events
        Map<String, Long> userList = new Hashtable<>(); // TODO: append attendee who check in the event
        ArrayList<String> notificationList = new ArrayList<String>(); // TODO: append notification to the list

        // Constructing the Event object
        Event newEvent = new Event(orgName, eventName, eventPosterID, description, geolocation, qrCodeBrowse, qrCodeCheckIn, eventAttendeeLimit, userList, eventDate, notificationList, location);

        // Now, convert  Event object to a Map or directly use the attributes to add to Firestore
        Map<String, Object> eventMap1 = EventUtility.evtomMap(newEvent);

        // added add() so, event ID will be automatically generated.
        // TODO: review with TEAM

        EventUtility.storeEventwnm(eventMap1, eventID);

        userDocRef.update("eventList", eventID).addOnFailureListener(new OnFailureListener() {
            @OptIn(markerClass = UnstableApi.class) @Override
            public void onFailure(@NonNull Exception e) {
                Log.w("Firestore", "Error updating document", e);
            }
        });
    }

}