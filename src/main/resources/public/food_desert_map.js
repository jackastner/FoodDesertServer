var map;
var queryRectangle;

var voronoiPolygons;

/* Specify how map events should be handled*/
var currentMapMode;
var MapMode = Object.freeze({
    coordinateQuery: {}, /* query to api placed for individual points */
    envelopeQuery: {}, /* user selects an envelope that is queried simultaneously. */
    voronoiQuery: {}, /* user selects an envelope and voronoi diagram is obtained for store in the envelope */
});

initControls();

/* Initializes listeners for controls overlay on map */
function initControls() {
    currentMapMode = MapMode.envelopeQuery;

    var submitEnvelopeButton = document.getElementById('queryButton');

    submitEnvelopeButton.onclick = handleQueryButtonClick;

    document.getElementById('query_coordinate').onclick = function() {
        submitEnvelopeButton.style.visibility='hidden';
        queryRectangle.setMap(null);
        currentMapMode = MapMode.coordinateQuery;
    }

    document.getElementById('query_envelope').onclick = function() {
        submitEnvelopeButton.style.visibility='visible';
        queryRectangle.setMap(map);
        currentMapMode = MapMode.envelopeQuery;
    }

    voronoiPolygons = [];
    document.getElementById('query_voronoi').onclick = function() {
        submitEnvelopeButton.style.visibility='visible';
        queryRectangle.setMap(map);
        currentMapMode = MapMode.voronoiQuery;
    }
}

/* Initializes the map element on the page as well as creating listeners to
 * handle events on the map element */
function initMap() {
    /* Create map element for this page */
    map = new google.maps.Map(document.getElementById('map'));

    /* Move map view window to user location if the user agrees.
     * Also only attempt to get location if connection is over https. */
    try {
        if((location.protocol === 'https:') && navigator.geolocation){
             navigator.geolocation.getCurrentPosition( function (latlng) {
                 map.setCenter({lat: latlng.coords.latitude, lng: latlng.coords.longitude});
                 queryRectangle = new google.maps.Rectangle({
                     fillOpacity: 0.33,
                     fillColor: '#FF0000',
                     map:map,
                     editable: true,
                     draggable: true,
                     bounds: {
                         north: latlng.coords.latitude + 0.1,
                         south: latlng.coords.latitude - 0.1,
                         east: latlng.coords.longitude + 0.1,
                         west: latlng.coords.longitude - 0.1
                     }
                 })
                 map.setZoom(10);
            });
        } else {
            setDefaultMap();
        }
    } catch (e) {
        setDefaultMap();
    }

    /* When the user clicks on the map a request is sent to the server.
     * The response is used to place a new marker on the map. */
    map.addListener('click', e => handleMapClick(e.latLng));
}

function setDefaultMap(){
    map.setCenter({lat: 0, lng:0});
    map.setZoom(1);
    var center = map.getCenter();

    queryRectangle = new google.maps.Rectangle({
        fillOpacity: 0.33,
        fillColor: '#FF0000',
        map:map,
        editable: true,
        draggable: true,
        bounds: {
            north: 10,
            south: -10,
            east: 10,
            west: -10
        }
    });
}

/*Handles a map click depending on the map mode selected*/
function handleMapClick(latLng){
    if(currentMapMode === MapMode.coordinateQuery){
        foodDesertPointQuery(latLng, addFoodDesertMarker);
    }
}

/* Handles click of submit buttons depending on the map mode */
function handleQueryButtonClick(){
    if(currentMapMode === MapMode.envelopeQuery){
        foodDesertEnvelopeQuery(queryRectangle.getBounds(), addGroceryStoreMarker);
    } else if (currentMapMode === MapMode.voronoiQuery){
        /*clear old polygons*/
        voronoiPolygons.forEach(function (p) {
            p.setMap(null);
        });
        voronoiPolygons = [];

        storeVoronoiQuery(queryRectangle.getBounds(), addVoronoiPolygon);
    }
}

/* Construct a GET parameter string for a rectangle defined by bounds*/
function prepareEnvelopeQuery(bounds){
    return  'lat0=' + bounds.getNorthEast().lat() + '&lng0=' + bounds.getNorthEast().lng() +
           '&lat1=' + bounds.getSouthWest().lat() + '&lng1=' + bounds.getSouthWest().lng();
}

/* Place a call to the server that will return an array of polygons representing the polygons of a Voronoi diagram
 * generated from the grocery stores with the area specified by bounds. callback is invoked once for each polygon. */
function storeVoronoiQuery(bounds, callback){
    var xhr = new XMLHttpRequest();
    var request = '/voronoi_stores?' + prepareEnvelopeQuery(bounds);

    xhr.open('GET', request, true);
    xhr.onload = function (e) {
        if (xhr.readyState === 4 && xhr.status === 200){
            var polygons = JSON.parse(xhr.responseText);
            polygons.forEach(function (s) {
                callback(s);
            });
        }
    }
    xhr.send(null);
}

/* Place a call to the server that will return all grocery stores in the envelope defined by the two coordinate
 * pairs. callback is invoked once for each store returned */
function foodDesertEnvelopeQuery(bounds, callback){
    var xhr = new XMLHttpRequest();
    var request = '/locate_stores?' + prepareEnvelopeQuery(bounds);

    xhr.open('GET', request, true);
    xhr.onload = function (e) {
        if (xhr.readyState === 4 && xhr.status === 200){
            var stores = JSON.parse(xhr.responseText);
            stores.forEach(function (s) {
                callback(s);
            });
        }
    }
    xhr.send(null);
}

/* Make an async call to the food desert server to determine if a given point is
 * in a food desert. Callback should be of type LatLng -> Boolean -> a */
function foodDesertPointQuery(latLng, callback){
    var xhr = new XMLHttpRequest();
    var request = '/is_in_food_desert?lat=' + latLng.lat() + '&lng=' + latLng.lng();

    xhr.open('GET', request, true);
    xhr.onload = function (e) {
        if (xhr.readyState === 4 && xhr.status === 200){
            var isInFoodDesert = JSON.parse(xhr.responseText);
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

/* Construct a new marker for the map that shows the location of a grocery store*/
function addGroceryStoreMarker(store){
    var marker = new google.maps.Marker({
        position: store,
        map: map,
    })

    var infowindow = new google.maps.InfoWindow({
        content: store.name
    })

    marker.addListener('click', function() {
        infowindow.open(map ,marker);
    })
}

function addVoronoiPolygon(polygon){
    var mapPolygon = new google.maps.Polygon({
        paths: polygon,
        fillOpacity: 0,
    });
    mapPolygon.setMap(map);;
    voronoiPolygons.push(mapPolygon);
}