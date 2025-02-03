import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class sevDeskApi {

    public static void main(String[] args) throws Exception{//IOException, InterruptedException, ParseException {
        // Eingabe vom Benutzer: Monat + Jahr
        Scanner scanner = new Scanner(System.in);
        System.out.println("Gib den Monat ein (z. B. 01 für Januar): ");
        String month = scanner.nextLine();
        System.out.println("Gib das Jahr ein (z. B. 2023): ");
        String year = scanner.nextLine();

        // Eingabe validieren
        if (!isValidMonthYear(month, year)) {
            System.out.println("Ungültiger Monat oder Jahr. Programm beendet.");
            return;
        }

        // Daten von der API holen
        //System.out.println("Monat: " + month + ", Jahr: " + year); test
        fetchInvoice(month, year);
    }

    // Methode zur Validierung von Monat und Jahr
    private static boolean isValidMonthYear(String month, String year) {
        try {
            int monthInt = Integer.parseInt(month);
            int yearInt = Integer.parseInt(year);
            return monthInt >= 1 && monthInt <= 12 && yearInt > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // API-Anfrage um Rechnungen für den angegebenen Monat und Jahr abzurufen
    public static void fetchInvoice(String month, String year) throws Exception{//IOException, InterruptedException, ParseException {
        final String API_URL = "https://my.sevdesk.de/api/v1/Invoice";
        final String API_KEY = "b08cf13e9c52876b7ad59730628e55c3";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String responseBody = response.body();
            //System.out.println("API Antwort: " + responseBody);  // Ausgabe der vollständigen Antwort test

            Gson gson = new Gson();

            // Die Antwort enthält ein Objekt mit dem Key "objects"
            ApiResponseInvoice apiResponseInvoice = gson.fromJson(responseBody, ApiResponseInvoice.class);

            // Wenn Rechnungen vorhanden sind --> topKunde finden
            if (apiResponseInvoice.objects != null && !apiResponseInvoice.objects.isEmpty()) {
                findTopCustomer(apiResponseInvoice.objects, month, year);
            } else {
                System.out.println("Keine Rechnungen gefunden.");
            }

        } else {
            System.out.println("Fehler beim Abrufen von Rechnungen: " + response.statusCode());
        }
    }

    private static void findTopCustomer(List<Invoice> invoices, String month, String year) throws Exception{//IOException, InterruptedException {
        double highestRevenue = 0;
        String topCustomerId = null;
        Contact contactTop = null;
        // Liste aller Kunden mit deren Umsatz
        List<CustomerRevenue> customerRevenues = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

        for (Invoice invoice : invoices) {
            String invoiceDate = invoice.getInvoiceDate();
            String contactId = invoice.getContact().getId();

            // Prüfe, ob die Rechnung im angegebenen Monat/Jahr liegt
            if (invoiceDate != null) {
                try {
                    java.util.Date date = sdf.parse(invoiceDate);
                    int invoiceYear = date.getYear() + 1900;  // Jahr extrahieren
                    int invoiceMonth = date.getMonth() + 1;  // Monat extrahieren

                    // Wenn das Jahr und der Monat übereinstimmen
                    if (invoiceYear == Integer.parseInt(year) && invoiceMonth == Integer.parseInt(month)) {
                        boolean found = false;
                        // Umsatz für jeden Kunden berechnen & in List abspeichern
                        for (CustomerRevenue cR : customerRevenues){
                            if (cR.getCustomerId().equals(contactId)){
                                cR.addRevenue(invoice.getSumNet());
                                found = true;
                                break;
                            }
                        }

                        if (!found){
                            customerRevenues.add(new CustomerRevenue(contactId, invoice.getSumNet()));
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Fehler beim Parsen des Datums: " + e.getMessage());
                }
            }
        }

        // aus der Liste aller Kunden, den "Top" finden
        for (CustomerRevenue cR : customerRevenues){
            //System.out.println(cR.getCustomerId() + " : " + cR.getTotalRevenue());
            if (cR.getTotalRevenue() > highestRevenue){
                highestRevenue = cR.getTotalRevenue();
                topCustomerId = cR.getCustomerId();
            }
        }

        // Gebe den umsatzstärksten Kunden aus
        if (topCustomerId != null) {
            contactTop = fetchContactDetails(topCustomerId);

            if (contactTop != null) {
                System.out.println("Umsatzstärkster Kunde: " + contactTop.getName() + " (Kundennummer: " +  contactTop.getId() + ")");
            } else {
                System.out.println("Keine Kontakte gefunden.");
            }
        } else {
            System.out.println("Keine Rechnungen im angegebenen Monat gefunden");
        }
    }

    private static Contact fetchContactDetails(String contactId) throws Exception{//IOException, InterruptedException {
        final String CONTACT_API_URL = "https://my.sevdesk.de/api/v1/Contact/" + contactId;  // API URL für Kontakt
        final String API_KEY = "b08cf13e9c52876b7ad59730628e55c3";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONTACT_API_URL))
                .header("Authorization", API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String responseBody = response.body();
            //System.out.println("Kontakt-API Antwort: " + responseBody);  // Ausgabe der vollständigen Antwort test

            // Verwenden von Gson, um das JSON zu parsen
            Gson gson = new Gson();
            // Die Antwort enthält ein Objekt mit dem Key "objects" --> extrahieren
            ApiResponseContact apiResponseContact = gson.fromJson(responseBody, ApiResponseContact.class);

            // Wenn Kontakte vorhanden sind -> den Ersten nehmen
            if (apiResponseContact.getObjects() != null && !apiResponseContact.getObjects().isEmpty()) {
                return apiResponseContact.getObjects().get(0);
            } else {
                System.out.println("Keine Rechnungen gefunden.");
                return null;
            }

        } else {
            System.out.println("Fehler beim Abrufen der Kontaktdaten: " + response.statusCode());
            return null;
        }
    }

    // Wrapper-Klasse für das API-Antwortformat Invoice
    private static class ApiResponseInvoice {
        List<Invoice> objects;
    }

    // Wrapper-Klasse für das API-Antwortformat Contact
    private static class ApiResponseContact {
        List<Contact> objects;

        public List<Contact> getObjects() {
            return objects;
        }

        public void setObjects(List<Contact> objects) {
            this.objects = objects;
        }
    }

    public class Invoice {
        private String invoiceDate;
        private double sumNet;
        private Contact contact;

        public Contact getContact() {
            return contact;
        }

        public void setContact(Contact contact) {
            this.contact = contact;
        }

        public double getSumNet() {
            return sumNet;
        }

        public void setSumNet(double sumNet) {
            this.sumNet = sumNet;
        }

        public String getInvoiceDate() {
            return invoiceDate;
        }

        public void setInvoicedate(String invoicedate) {
            this.invoiceDate = invoicedate;
        }
    }


    public static class Contact {
        private String id;
        private String name;

        // Getter und Setter
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    private static class CustomerRevenue {
        private String customerId;
        private double totalRevenue;

        public CustomerRevenue(String customerId, double totalRevenue){
            this.customerId = customerId;
            this.totalRevenue = totalRevenue;
        }

        public String getCustomerId() {
            return customerId;
        }

        public double getTotalRevenue() {
            return totalRevenue;
        }

        public void addRevenue(double revenue){
            this.totalRevenue += revenue;
        }
    }

}
