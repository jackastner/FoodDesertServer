# Setup Project

* Install sqlite 3.24
* Install spatialite 4.3.0
* Install JDK8
* `git clone https://github.com/jackastner/FoodDesertServer.git`
* Enter a google api key valid for use with the Google Places API into the file `google_api_key` in the root of this repository.
* `./gradlew test`

If the Gradle build succeeds and all tests pass, the project has been successfully setup. The most common reason for failing tests is a missing or invalid API key.
 
# Run Server
First, follow the the steps in Project Setup. You can then choose to run the server directly through Gradle or by building and executing a jar file.
 
## Run with Gradle
`./gradlew run`
This will start the server using the google api key entered during setup and using the default database file (`data.db`).
  
## Build the Jar
* `./gradlew jar`
* `java -jar build/libs/FoodDesertServer.jar` to see usage information.

      Usage: java -jar FoodDesertServer.jar database_file [google_api_key]
      	database_file: SqLite database file containing tables created by this server.
      	google_api_key: a valid key for the Google Places API. If omitted, this
      		program will look for a Java properties file containing a key value pair:
      		google_api_key=$YOUR_API_KEY
      		
* `java jar build/libs/FoodDesertServer.jar data.db` to run in the default configuration. 

# Use Server
Once the server is running, the web interface is available at http://localhost:4567/food_desert_map.html . The interface provides access to methods defined in FoodDesertQueryHandler and displays results on a map.
 
---
 
# Unimplemented Features

* Resolve error: GEOS error: TopologyException: Input geom 1 is invalid: Too few points in geometry component at or near point
  * Possibly resolve by checking geom.isValid() and failing gracefully if not.
  * Could call MakeValid on geometries in SQL.
* Graceful handling of failed Places API queries.
* Obtain and display polygon(s) representing areas that are food deserts.
  * Generate these polygons using walking distance (rather than Euclidean distance). 
  * Account for location in these polygons. In Suburban and rural areas, distance to a grocery store can be much larger without being a food desert.
* Find alternative data sources. Google Places API will be paid only soon.
  * PG county data website provides grocery store locations that could be loaded into the database. Presumably other data sources like this exist.
  * I would like to find a solution that can get data for arbitrary locations like the Places API.
* Switch user interface to OSM.
* Document API.
