package com.example.evenz;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class EventDetailsActivity  extends AppCompatActivity {
    private String eventID;
    private ImageView eventPoster, homeButton;
    private TextView eventLocation, eventDetail, eventWelcomeNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle b = getIntent().getExtras();
        assert b != null;
        String role = b.getString("role");
        eventID = b.getString("eventID");

        if (Objects.equals(role, "attendee")) {
            setContentView(R.layout.attendee_event_info_sign_up);
            setupAttendeeView();
        } else {
            setContentView(R.layout.org_event_info);
            setupOrganizerView();
        }
        if (!eventID.isEmpty()) {
            fetchEventDetailsAndNotifications(eventID);
        }
    }

    private void setupAttendeeView() {
        eventPoster = findViewById(R.id.poster_attendee_eventInfo);
        eventLocation = findViewById(R.id.loc_attendee_eventInfo);
        eventDetail = findViewById(R.id.info_attendee_eventInfo);
        eventWelcomeNote = findViewById(R.id.attendee_event_detail_welcome);
        homeButton = findViewById(R.id.home_event_details_attendee);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomePageIntent("attendee", eventID);
            }
        });
        TextView signUpButton = findViewById(R.id.sign_up_button);
        View lightButton= findViewById(R.id.light_green_button_rect);//this is for changing the color of the button
        String source = getIntent().getStringExtra("source");
        if ("homepage".equals(source)) {
            lightButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.red)); //If they need checkout button set color to be RED
            signUpButton.setText("Check Out");
        } else if ("browse".equals(source)) {
            signUpButton.setText("Sign Up");
        }
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get the device ID
                String userID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                String source = getIntent().getStringExtra("source");
                if ("homepage".equals(source)) {
                    new AlertDialog.Builder(EventDetailsActivity.this)
                            .setTitle("Check Out Confirmation")
                            .setMessage("Are you sure you want to check out of the event?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                // Call method to remove event from user's list of events
                                FirebaseUserManager firebaseUserManager = new FirebaseUserManager();
                                firebaseUserManager.removeEventFromUser(userID, eventID);

                                EventUtility.removeAttendeeFromEvent(userID, eventID);//remove user from event

                                // Display toast message
                                Toast.makeText(EventDetailsActivity.this, "Successfully Checked Out of Event!!!", Toast.LENGTH_SHORT).show();

                                // Close the activity and go back
                                finish();
                            })
                            .setNegativeButton("No", null)
                            .show();
                } else if ("browse".equals(source)) {
                    // Call  addUserToEvent method. This will add the user to the event
                    EventUtility.addUserToEvent(userID, eventID);

                    // Add user to the list of events they've signed up for
                    FirebaseUserManager firebaseUserManager = new FirebaseUserManager();
                    firebaseUserManager.addEventToUser(userID, eventID);

                    // Display toast message
                    Toast.makeText(EventDetailsActivity.this, "Successfully Signed Up for Event!!!", Toast.LENGTH_SHORT).show();

                    // Close the activity and go back
                    finish();
                }
            }
        });
        eventLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMapIntent(eventID, "attendee", (String) eventLocation.getText());
            }
        });
    }

    private void setupOrganizerView() {
        eventPoster = findViewById(R.id.poster_org_eventInfo);
        eventLocation = findViewById(R.id.loc_org_eventInfo);
        eventDetail = findViewById(R.id.info_org_eventInfo);
        eventWelcomeNote = findViewById(R.id.org_event_detail_welcome);
        homeButton = findViewById(R.id.home_event_details_org);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomePageIntent("organizer", eventID);
            }
        });

            ImageView shareQR = findViewById(R.id.shareQR_event_details);
            shareQR.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    QRGenerator test = new QRGenerator();
                    Bitmap bitmap = test.generate(eventID, "SignUp", 400, 400);
                    Uri bitmapUri = saveBitmapToCache(bitmap);

                Intent intent = new Intent(EventDetailsActivity.this, ShareQRActivity.class);

                intent.putExtra("eventID", eventID);
                intent.putExtra("BitmapImage", bitmapUri.toString());
                startActivity(intent);
            }
        });

        eventLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMapIntent(eventID, "organizer", (String) eventLocation.getText());
            }
        });
    }

    private void openMapIntent(String eventID, String role, String addressString) {
        Intent intent = new Intent(EventDetailsActivity.this, MapsActivity.class);

        intent.putExtra("eventID", eventID);
        intent.putExtra("role", role);
        intent.putExtra("addressString", addressString);
        intent.putExtra("from", "eventDetails");
        startActivity(intent);
    }

    private void openHomePageIntent(String role, String eventID) {
        Intent intent = new Intent(new Intent(EventDetailsActivity.this, HomeScreenActivity.class));
        Bundle b = new Bundle();
        b.putString("role", role);
        b.putString("eventID", eventID);
        intent.putExtras(b);
        startActivity(intent);
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs(); // don't forget to make the directory
            FileOutputStream stream = new FileOutputStream(cachePath + "/image.png"); // overwrites this image every time
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            File imagePath = new File(getCacheDir(), "images");
            File newFile = new File(imagePath, "image.png");
            return Uri.fromFile(newFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void fetchEventDetailsAndNotifications(String eventId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot != null && documentSnapshot.exists()) {
                Event event = documentSnapshot.toObject(Event.class);
                // Directly update the TextView with the event's location

                if (event != null) {
                    String eventName = event.getEventName();
                    eventWelcomeNote.setText("\uD83D\uDC4B Welcome to " + event.getEventName() + "! \uD83D\uDE80");
                    eventDetail.setText(event.getDescription());
                    eventDetail.setMovementMethod(new ScrollingMovementMethod());
                    eventLocation.setText(event.getLocation());
                    displayImage(event.getEventPosterID(), eventPoster);
                }
            } else {
                // TODO: Handle the case where the event doesn't exist in the database
            }
        }).addOnFailureListener(e -> {
            // TODO: handle errors
        });
    }

    private void displayImage(String imageID, ImageView imgView)
    {
        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
        StorageReference photoReference= storageReference.child("images/" + imageID);

        final long ONE_MEGABYTE = 1024 * 1024;
        photoReference.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                imgView.setImageBitmap(bmp);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Toast.makeText(EventDetailsActivity.this, "No Such file or Path found!!", Toast.LENGTH_LONG).show();
            }
        });
    }
}