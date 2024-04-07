package com.example.evenz;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class FirebaseUserManager {

    private final FirebaseFirestore db;
    private final CollectionReference ref;

    public FirebaseUserManager() {
        this.db = FirebaseFirestore.getInstance();
        this.ref = db.collection("users");
    }

    public Task<Void> submitUser(User user) {
        if (user == null) {
            return Tasks.forException(new IllegalArgumentException("User cannot be null"));
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", user.getName());
        userMap.put("profilePicID", user.getProfilePicID());
        userMap.put("phone", user.getPhone());
        userMap.put("email", user.getEmail());
        // No need to put "userId" in the map since it's used as document ID.
        userMap.put("userType", user.getUserType());
        userMap.put("eventsSignedUpFor", user.getEventsSignedUpFor());
        userMap.put("checkedInEvent", user.getCheckedInEvent());
        userMap.put("notificationEnabled", user.getNotificationsEnabled());
        userMap.put("locationEnabled", user.getLocationEnabled());


        // Use userId as the document ID
        return ref.document(user.getUserId()).set(userMap, SetOptions.merge());
    }

    // submitOrganizer method that will sumbit an organizer to the database which is just a user with only a User ID, Name, UserType, and checkedInEvent


    // Create a method to update a user document in the database to add an event to the eventsSignedUpFor list
    public Task<Void> addEventToUser(String userId, String eventId) {
        return ref.document(userId).update("eventsSignedUpFor", FieldValue.arrayUnion(eventId));
    }

    // Remove user from event
    public Task<Void> removeEventFromUser(String userId, String eventId) {
        return ref.document(userId).update("eventsSignedUpFor", FieldValue.arrayRemove(eventId));
    }

    // Add a checkin event to a user
    public Task<Void> checkInUser(String userId, String eventId) {
        return ref.document(userId).update("checkedInEvent", eventId);
    }

    // Create a method that will return the eventID of the checked-in event for a given user
    public Task<String> getCheckedInEventForUser(String userId) {
        return ref.document(userId).get().continueWith(task -> {
            if (task.isSuccessful()) {
                return task.getResult().getString("checkedInEvent");
            }
            return null;
        });
    }

    // Create a method that will return all of the attendes for a given event it uses the getUser method to get the user object
    public Task<List<User>> getAttendeesForEvent(String eventId) {
        return ref.whereArrayContains("eventsSignedUpFor", eventId).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                // If the task failed, propagate the exception
                return Tasks.forException(task.getException());
            }
            if (task.getResult() == null) {
                // If the result is null, propagate an exception or handle accordingly
                return Tasks.forException(new IllegalStateException("Result is null"));
            }

            List<Task<User>> tasks = new ArrayList<>();
            for (QueryDocumentSnapshot document : task.getResult()) {
                // As the schema shows, the user ID is the document ID, not a field within the document
                String userId = document.getId(); // Document ID is the userId
                // Construct the User object using the schema from the image
                User user = new User(
                        userId,
                        document.getString("name"),
                        document.getString("phone"),
                        document.getString("email"),
                        document.getString("profilePicID"),
                        document.getString("userType"),
                        document.getBoolean("notificationEnabled"),
                        document.getBoolean("locationEnabled")
                );
                // No need to add a task since we have the User object; we add the User object directly to the list
                tasks.add(Tasks.forResult(user));
            }
            // Wait for all tasks to complete and collect the User objects
            return Tasks.whenAllSuccess(tasks);
        });
    }



    // Create user method that will return a user object for a given userId
    public Task<User> getUser(String userId) {
        return ref.document(userId).get().continueWith(task -> {
            if (task.isSuccessful()) {
                QueryDocumentSnapshot document = (QueryDocumentSnapshot) task.getResult();
                User user = new User(
                        document.getString("userId"),
                        document.getString("name"),
                        document.getString("phone"),
                        document.getString("email"),
                        document.getString("profilePicID"),
                        document.getString("userType"),
                        document.getBoolean("notificationEnabled"),
                        document.getBoolean("locationEnabled")
                );
                // Initialize the eventsSignedUpFor ArrayList if it exists in the document
                List<String> eventsSignedUpFor = (List<String>) document.get("eventsSignedUpFor");
                if (eventsSignedUpFor != null) {
                    user.setEventsSignedUpFor(new ArrayList<>(eventsSignedUpFor));
                }
                return user;
            }
            return null;
        });
    }

    /**
     * A firebase task that given the device id of the user returns the eventID
     * they are currently attending or hosting
     * @param deviceID The id of the device (userID)
     * @return the eventID they are currently attending or hosting
     */
    public Task<String> getEventID(String deviceID) {
        final List<String> eventID = new ArrayList<>();

        return FirebaseFirestore.getInstance()
                .collection("users")
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            if (document.contains("eventList")) {
                                if (document.getId().equals(deviceID)) {
                                    eventID.add(document.getString("eventList"));
                                    return eventID.get(0);
                                }
                            }
                        }
                        eventID.add("N");
                        return eventID.get(0);
                    } else {
                        throw task.getException();
                    }
                });
    }

    public Task<String> getEventName(String userId) {
        return ref.document(userId).get().continueWith(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                List<String> eventsSignedUpFor = (List<String>) document.get("eventsSignedUpFor");
                if (eventsSignedUpFor != null && !eventsSignedUpFor.isEmpty()) {
                    return eventsSignedUpFor.get(0);
                } else {
                    // Handle the case where eventsSignedUpFor is null or empty.
                    throw new NoSuchElementException("No events signed up for.");
                }
            } else {
                // Handle the unsuccessful task, e.g., by throwing an exception.
                throw new Exception("Failed to fetch document: " + task.getException());
            }
        });
    }


    /**
     * A firebase task that given the device id of the user returns their userType
     * @param deviceID The id of the device (userID)
     * @return the devices usertype being Attendee, Organizer, or Admin
     */
    public Task<String> getUserType(String deviceID) {
        final List<String> userID = new ArrayList<>();

        return FirebaseFirestore.getInstance()
                .collection("users")
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            if (document.contains("userType")) {
                                if (document.getId().equals(deviceID)) {
                                    userID.add(document.getString("userType"));
                                    return userID.get(0);
                                }
                            }
                        }
                        userID.add("N");
                        return userID.get(0);
                    } else {
                        throw task.getException();
                    }
                });
    }

    // Additional methods for user management can be added here...
}
