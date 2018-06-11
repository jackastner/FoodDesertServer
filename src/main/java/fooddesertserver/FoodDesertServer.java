package fooddesertserver;

import static spark.Spark.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import fooddesertdatabase.FoodDesertDatabase;
import googleplacesclient.GooglePlacesClient;

public class FoodDesertServer {

    private static void printUsage() {
        System.out.println("Usage: java -jar FoodDesertServer.jar database_file [google_api_key]");
        System.out.println("\tdatabase_file: sqlite database file containing tables created by this server");
        System.out.println("\tgoogle_api_key: a valid key for the Google Places API. If ommited, this\n\t\tprogram will look for a Java properites file containing a key value pair:");
        System.out.println("\t\tgoogle_api_key=$YOUR_API_KEY");
    }

    private static void setupRoutes(FoodDesertQueryHandler queryHandler) {
        /*used to build points for url parameters*/
        GeometryFactory geoFactory = new GeometryFactory();

        staticFiles.location("/public");

        get("/is_in_food_desert", (request, response) -> {
            double lng = Double.parseDouble(request.queryParams("lng"));
            double lat = Double.parseDouble(request.queryParams("lat"));
            Point location = geoFactory.createPoint(new Coordinate(lng,lat));
            boolean isInFoodDesert = queryHandler.isInFoodDesert(location);
            return String.valueOf(isInFoodDesert);
        });
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, SQLException {
        if(args.length < 1) {
            printUsage();
            return;
        }

        String dbFile = args[0];

        /*get google API key from either arguments or file*/
        String googleApiKey;
        if(args.length < 2) {
            if (Files.exists(Paths.get("google_api_key"), LinkOption.NOFOLLOW_LINKS)) {
                 Properties props = new Properties();
                 try (FileInputStream in = new FileInputStream("google_api_key")) {
                     props.load(in);
                 }
                 googleApiKey = props.getProperty("google_api_key");
            } else {
                printUsage();
                return;
            }
        } else {
            googleApiKey = args[1];
        }

        /*load existing database or create a new one as needed */
        FoodDesertDatabase database;
        Path dbPath = Paths.get(dbFile);
        if (Files.exists(dbPath, LinkOption.NOFOLLOW_LINKS)) {
            database = new FoodDesertDatabase(dbFile);
        } else {
            database = FoodDesertDatabase.createDatabase(dbFile);
        }

        GooglePlacesClient client = new GooglePlacesClient(googleApiKey);
        FoodDesertQueryHandler queryHandler = new FoodDesertQueryHandler(database, client);

        setupRoutes(queryHandler);
    }
}
