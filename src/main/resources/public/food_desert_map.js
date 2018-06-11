var map;

/* Initializes the map element on the page as well as creating listeners to
 * handle events on the map element */
function initMap() {
    /* Create map element for this page */
    map = new google.maps.Map(document.getElementById('map'));

    /* Move map view window to user location if the user agrees */
    if(navigator.geolocation){
        navigator.geolocation.getCurrentPosition( function (latlng) {
            map.setCenter({lat: latlng.coords.latitude, lng: latlng.coords.longitude});
            map.setZoom(10);
        });
    }

    /* When the user clicks on the map a request is sent to the server.
     * The response is used to place a new marker on the map. */
    map.addListener('click', e => foodDesertPointQuery(e.latLng, addFoodDesertMarker));
}

/* Make an async call to the food desert server to determine if a given point is
 * in a food desert. Callback should be of type LatLng -> Boolean -> a */
function foodDesertPointQuery(latLng, callback){
        var xhr = new XMLHttpRequest();
        var request = '/is_in_food_desert?lat=' + latLng.lat() + '&lng=' + latLng.lng();

        xhr.open('GET', request, true);
        xhr.onload = function (e) {
            if (xhr.readyState === 4 && xhr.status === 200){
                var isInFoodDesert = xhr.responseText == 'true';
                callback(latLng, isInFoodDesert);
            }
        }
        xhr.send(null);
}

/* Construct a new maker for the map  to indicate if a location is in a
 * food desert */
function addFoodDesertMarker(latLng, isInFoodDesert){
    new google.maps.Marker({
        position: latLng,
        map: map,
        label: isInFoodDesert ? 'T' : 'F'
    });
}