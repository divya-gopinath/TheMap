package gopinath.divya.themap;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String[] NEED_PERM = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET};
    private static final int PERMISSIONS_CODE = 1;
    private static final int DEFAULT_ZOOM = 10;
    private static final double METERS_PER_MILE = 1609.34;
    private static final long DAY_IN_MILLISEC = 86400000;
    private static final int NOTIFICATION_DELAY = 10000;
    private static final int CIRCLE_STROKE = Color.BLUE;

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private GeofencingClient mGeofencingClient;
    private Optional<PendingIntent> pendingIntent;
    private Geocoder geocoder;
    private String currentDestination;
    private ArrayList<Marker> currentMarkers = new ArrayList<>();
    private ArrayList<Circle> currentCircles = new ArrayList<>();
    private Geofence geofenceToAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mFusedLocationClient = getFusedLocationProviderClient(this);
        geocoder = new Geocoder(getApplicationContext(), Locale.US);
        pendingIntent = Optional.empty();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        requestPermissions(NEED_PERM, PERMISSIONS_CODE);
        // Get last location or default to Sydney
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            LatLng boston = new LatLng(42, 71);
            setMarkerMoveMap(boston);
            Log.d("perm", "boston");
        } else {
            // Get last known recent location using new Google Play Services SDK (v11+)
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // GPS location can be null if GPS is switched off
                            if (location != null) {
                                LatLng currentLoc = new LatLng(location.getLatitude(), location.getLongitude());
                                setMarkerMoveMap(currentLoc);
                                setSearchToPosition(currentLoc);
                            }
                        }
                    });
        }
        mMap.moveCamera(CameraUpdateFactory.zoomTo(DEFAULT_ZOOM));
        initPlaceAuto();
        initRadiusPicker();
        addDragListener();
        initAlarmSwitch();
        mGeofencingClient = LocationServices.getGeofencingClient(this);

    }

    private void initPlaceAuto() {
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                if (place.getName() != null) { currentDestination = place.getName().toString(); }
                else { currentDestination = place.getAddress().toString(); }
                setMarkerMoveMap(place.getLatLng());
            }

            @Override
            public void onError(Status status) {
                Log.i("perm", "An error occurred: " + status);
            }
        });
    }

    private void initRadiusPicker() {
        NumberPicker radiusPicker = findViewById(R.id.radius);
        radiusPicker.setMinValue(1);
        radiusPicker.setMaxValue(100);
        radiusPicker.setValue(1);
        radiusPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int oldVal, int newVal) {
                Circle currentCircle = currentCircles.get(0);
                double radiusInMiles = (double) ((NumberPicker) findViewById(R.id.radius)).getValue();
                double radius = radiusInMiles * METERS_PER_MILE;
                currentCircle.setRadius(radius);
            }
        });
    }

    private void removeAllMarkers() {
        for (Marker m : currentMarkers) {
            m.remove();
        }
        for (Circle c : currentCircles) {
            c.remove();
        }
        currentMarkers = new ArrayList<>();
        currentCircles = new ArrayList<>();
    }

    private void setMarkerMoveMap(LatLng moveHere) {
        removeAllMarkers();
        double radiusInMiles = (double) ((NumberPicker) findViewById(R.id.radius)).getValue();
        double radius = radiusInMiles * METERS_PER_MILE;
        Marker markerToAdd = mMap.addMarker(new MarkerOptions().position(moveHere).title("Destination").draggable(true));
        Circle circleToAdd = mMap.addCircle(new CircleOptions().center(moveHere).radius(radius).strokeColor(CIRCLE_STROKE));
        currentMarkers.add(markerToAdd);
        currentCircles.add(circleToAdd);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(moveHere));
    }

    private void addDragListener() {
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {

            @Override
            public void onMarkerDragStart(Marker marker) {
                String message = "You are changing your destination.";
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                return;
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                LatLng newPos = marker.getPosition();
                setMarkerMoveMap(newPos);
                setSearchToPosition(newPos);
            }
        });
    }

    private void initAlarmSwitch() {
        Switch alarmSwitch = (Switch) findViewById(R.id.alarmSwitch);
        alarmSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked) {
                    createConfirmation();
                    // set pending intent
                } else {
                    if (pendingIntent.isPresent()) {
                        pendingIntent.get().cancel();
                        Log.d("geofence", "PENDING EVENT PRESENT NOW CANCELLED");
                    }
                    pendingIntent = Optional.empty();
                    String message = "ALARM CANCELLED";
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setSearchToPosition(LatLng position) {
        try {
            PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                    getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
            List<Address> addressList = geocoder.getFromLocation(position.latitude, position.longitude, 1);
            if (addressList.isEmpty()) { throw new IOException(); }
            Address possAddress = addressList.get(0);
            Log.d("ADDRESS", possAddress.toString());
            String featureName = possAddress.getFeatureName();
            String addressLine = possAddress.getAddressLine(0);
            if (featureName != null && !featureName.matches("\\d+")) {
                autocompleteFragment.setText(featureName);
                currentDestination = featureName;
            } else if (addressLine != null) {
                String fullAdd = getFullAddress(possAddress);
                autocompleteFragment.setText(fullAdd);
                currentDestination = fullAdd;
            } else { throw new IOException(); }
        } catch (IOException e) {
            String message = "Cannot find place. Using exact coordinates instead.";
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                    getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
            autocompleteFragment.setText(position.toString());
            currentDestination = position.toString();
        }
    }

    private void createConfirmation() {
        // Get information
        NumberPicker numPick = (NumberPicker) findViewById(R.id.radius);
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        int radius = numPick.getValue();
        String destination = currentDestination;

        // Build alert
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String message = "ALARM ON";
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                createGeofence();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Switch alarmSwitch = findViewById(R.id.alarmSwitch);
                alarmSwitch.setChecked(false);
            }
        });
        builder.setTitle("Set alarm?");
        builder.setMessage(String.format("You will be notified when you are within %s miles of your destination: %s",
                radius, destination));

        // Create and show
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private String getFullAddress(Address address) {
        String fullAddress = "";
        int maxIndex = address.getMaxAddressLineIndex();
        for (int i=0; i<=maxIndex; i++) { fullAddress += address.getAddressLine(i) + " "; }
        return fullAddress;
    }

    private void createGeofence() {
        LatLng origin = currentMarkers.get(0).getPosition();
        float radius = (float) currentCircles.get(0).getRadius();
        geofenceToAdd = new Geofence.Builder()
                .setRequestId("alarm")
                .setCircularRegion(
                        origin.latitude,
                        origin.longitude,
                        radius
                )
                .setExpirationDuration(DAY_IN_MILLISEC)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(NOTIFICATION_DELAY)
                .build();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            String message = "Please enable location permissions and try again.";
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        mGeofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("geofence", "ADDED LISTENER SUCCESSFULL");
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        String message = "Failed to create alarm. Please try again.";
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(geofenceToAdd);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (pendingIntent.isPresent()) {
            return pendingIntent.get();
        }
        Intent intent = new Intent(this, AlarmActivity.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        pendingIntent = Optional.of(PendingIntent.getActivity(this,
                12345, intent, PendingIntent.FLAG_CANCEL_CURRENT));
        return pendingIntent.get();
    }

    @Override
    public void onResume() {
        super.onResume();

    }


}
