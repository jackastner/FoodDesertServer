package nominatimclient;

import java.io.IOException;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;

import fr.dudie.nominatim.client.JsonNominatimClient;
import fr.dudie.nominatim.client.NominatimClient;
import fr.dudie.nominatim.client.request.NominatimSearchRequest;
import fr.dudie.nominatim.model.Address;
import fr.dudie.nominatim.model.BoundingBox;
import fr.dudie.nominatim.model.Element;

/*
 * I really want to use this to keep my project completely open source,
 *  but the results are just not good enough.
 */

public class NominatimGroceryStoreSearch {

    public static void main(String[] args) {

        final SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        final ClientConnectionManager connexionManager = new SingleClientConnManager(null, registry);

        final HttpClient httpClient = new DefaultHttpClient(connexionManager, null);

        final String baseUrl = "https://nominatim.openstreetmap.org/";
        final String email = "john.h.kastner@gmail.com";
        NominatimClient nominatimClient = new JsonNominatimClient(baseUrl, httpClient, email);

        // westlimit=-76.977381; southlimit=38.961969; eastlimit=-76.904633;
        // northlimit=39.032998
        BoundingBox queryBounds = new BoundingBox();
        queryBounds.setNorth(39.032998);
        queryBounds.setWest(-76.997381);

        queryBounds.setSouth(38.961969);
        queryBounds.setEast(-76.884633);

        System.out.println(queryBounds);

        NominatimSearchRequest req = new NominatimSearchRequest();
        req.setBounded(true);
        req.setViewBox(queryBounds);
        req.setQuery("Greengrocers");

        // System.out.println("query prepared");
        // System.out.println(req.getQueryString());

        try {
            List<Address> results = nominatimClient.search(req);

            int idx = 0;
            for (Address adr : results) {
                idx++;
                System.out.printf("Result %d: %s [", idx, adr.getDisplayName());
                if (adr.getAddressElements() != null) {
                    for (Element e : adr.getAddressElements()) {
                        System.out.print(e.toString());
                    }
                }
                System.out.println("]");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
