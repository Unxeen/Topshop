package personal.projects.TopShop.controller.rest;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import personal.projects.TopShop.common.EmailSender;
import personal.projects.TopShop.dao.ClientRepository;
import personal.projects.TopShop.dao.LineProductRepository;
import personal.projects.TopShop.dao.ProductRepository;
import personal.projects.TopShop.dao.SaleRepository;
import personal.projects.TopShop.domaine.payment.CreatePayment;
import personal.projects.TopShop.domaine.payment.CreatePaymentRequest;
import personal.projects.TopShop.domaine.payment.CreatePaymentResponse;
import personal.projects.TopShop.service.exception.BusinessException;
import personal.projects.TopShop.service.models.LineProduct;
import personal.projects.TopShop.service.models.Product;
import personal.projects.TopShop.service.models.Sale;

import java.util.*;

@RestController
@AllArgsConstructor
@CrossOrigin
public class PaymentController {

    private ClientRepository clientRepository;
    private ModelMapper modelMapper;
    private LineProductRepository lineProductRepository;
    private SaleRepository saleRepository;
    private ProductRepository productRepository;
    private EmailSender emailSender;

    private final String endpointSecret = "whsec_c1aaf7e9dd858fd61513da8e97eb8df1b406d79409b50aac9f3df22ddcbe6891";


    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'SUPPORT')")
    @PostMapping("/create-payment-intent/{username}")
    public ResponseEntity<Object> makePayment(@PathVariable("username") String username,
                                             @RequestHeader HttpHeaders headers) throws StripeException {


        // Fetch all cart items
        List<LineProduct> currentCart = lineProductRepository.findAllByClient_Username(username)
                .stream()
                .toList();



        // Check cart emptiness
        if (currentCart.isEmpty()){
            return new ResponseEntity<>("Cart is empty!", HttpStatus.BAD_REQUEST);
        }



        // Set up parameters
        Map<String, String> metadata = new HashMap<>();
        currentCart.forEach(
                lp -> metadata.put(String.format("id_%s", lp.getId()), String.valueOf(lp.getId()))
        );
        metadata.put("client", username); 



        // Create payment intent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(getTotal(currentCart))
                .setCurrency("usd")
                .putAllMetadata(metadata)
                .build();
        PaymentIntent paymentIntent = PaymentIntent.create(params);



        return new ResponseEntity<>(

                CreatePaymentResponse.builder()
                .clientSecret(paymentIntent.getClientSecret())
                .build(),

                HttpStatus.CREATED);
    }



    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'SUPPORT')")
    @PostMapping("/create-payment-intent")
    public ResponseEntity<Object> makeSinglePayment(@RequestBody CreatePaymentRequest request,
                                                    @RequestHeader HttpHeaders headers) throws StripeException {

        Long pid = request.getProductId();
        String username = request.getUsername();
        int pquantity = request.getQuantity();

        // Fetch payment target product
        Product product = productRepository.findById(pid).orElseThrow(
                () -> new BusinessException(String.format("No product with id:%s exists", pid))
        );

        Double total = pquantity * product.getPrice() * 100;
        Long stripeTotal = total.longValue();
        System.out.println(stripeTotal);

        // Set up parameters
        Map<String, String> metadata = new HashMap<>();
        metadata.put(
                String.format(
                        "pid_%s",
                        pid
                ),
                String.valueOf(pid)
        );
        metadata.put(
                "pqty",
                Integer.toString(pquantity)
        );
        metadata.put(
                "client",
                username
        );



        // Create payment intent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(stripeTotal)
                .setCurrency("usd")
                .putAllMetadata(metadata)
                .build();
        PaymentIntent paymentIntent = PaymentIntent.create(params);



        return new ResponseEntity<>(

                CreatePaymentResponse.builder()
                        .clientSecret(paymentIntent.getClientSecret())
                        .build(),

                HttpStatus.CREATED);
    }



    public Long getTotal(List<LineProduct> cart){
        List<Double> totals = new ArrayList<>();
        cart.forEach(createPaymentItem -> {
            totals.add(createPaymentItem.getTotalPrice() * 100);
        });
        return totals
                .stream()
                .mapToLong(Double::longValue).sum();
    }


    @PostMapping("/webhook")
    public ResponseEntity<Object> stripeHook(@RequestHeader HttpHeaders headers,
                                             @RequestBody String requestBody) throws MessagingException {

        String sigHeader = headers.getFirst("Stripe-Signature");
        Event event = null;

        try {

            assert sigHeader != null;
            event = Webhook.constructEvent(requestBody, sigHeader, endpointSecret);

        } catch (SignatureVerificationException e) {

            return new ResponseEntity<>(
                    "Invalid signature",
                    HttpStatus.BAD_REQUEST
            );

        }



        // Deserialize the nested object inside the event
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        PaymentIntent stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = (PaymentIntent) dataObjectDeserializer.getObject().get();
//            System.out.println(stripeObject.getMetadata());
        } else {
            // Deserialization failed, probably due to an API version mismatch.
            // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
            // instructions on how to handle this case, or return an error here.
            return new ResponseEntity<>("Deserialization failed", HttpStatus.BAD_REQUEST);
        }





        // Handle the event
        switch (event.getType()) {
            case "payment_intent.payment_failed": {
                // Then define and call a function to handle the event payment_intent.payment_failed
                System.out.println("Payment failed");
                break;
            }
            case "payment_intent.processing": {
                // Then define and call a function to handle the event payment_intent.processing
                System.out.println("Payment processing");
                break;
            }
            case "payment_intent.succeeded": {

//                Receipt items
                StringBuilder productsHtml = new StringBuilder();

                Map<String, String> metadata = stripeObject.getMetadata();

//                FOR CART CHECKOUTS
                List<LineProduct> payedForItems = new ArrayList<>();

//                FOR SINGLE PRODUCT CHECKOUTS
                List<Product> payedForProducts = new ArrayList<>();
                List<Integer> productQtys = new ArrayList<>();

//                TOTAL PRICE FOR EMAIL RECEIPT
                List<Double> totalPrices = new ArrayList<>();

                metadata.forEach((key, value) -> {
                    if (key.startsWith("id")) {
                        payedForItems.add(
                                lineProductRepository.findById(Long.parseLong(value)).get()
                        );
                    } else if (key.startsWith("pid")) {
                        payedForProducts.add(
                                productRepository.findById(Long.parseLong(value)).get()
                        );
                    } else if (key.startsWith("pqty")) {
                        productQtys.add(Integer.parseInt(value));
                    }
                });
                payedForItems.forEach(
                        lp -> {
                            saleRepository.save(
                                    Sale.builder()
                                            .owner(lp.getProduct().getOwner())
                                            .buyer(clientRepository.findByUsername(metadata.get("client")).get())
                                            .createdAt(new Date())
                                            .product(lp.getProduct().getLabel())
                                            .totalPrice(lp.getTotalPrice())
                                            .quantity(lp.getQuantity())
                                            .build());

                            Product product = productRepository.findById(lp.getProduct().getId()).get();
                            product.setStock(product.getStock() - lp.getQuantity());
                            productRepository.save(product);

                            totalPrices.add(lp.getTotalPrice());

                            productsHtml.append("<tr>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px;'>").append(lp.getProduct().getLabel()).append("</td>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>").append(lp.getQuantity()).append("</td>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>$").append(lp.getProduct().getPrice()).append("</td>")
                                    .append("</tr>");


                        }
                );


                payedForProducts.forEach(
                        p -> {
                            saleRepository.save(
                                    Sale.builder()
                                            .owner(p.getOwner())
                                            .buyer(clientRepository.findByUsername(metadata.get("client")).get())
                                            .createdAt(Calendar.getInstance().getTime())
                                            .product(p.getLabel())
                                            .totalPrice(p.getPrice() * productQtys.get(0))
                                            .quantity(productQtys.get(0))
                                            .build()
                            );

                            Product product = payedForProducts.get(0);
                            product.setStock(product.getStock() - productQtys.get(0));
                            productRepository.save(product);

                            totalPrices.add(p.getPrice());

                            productsHtml.append("<tr>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px;'>").append(p.getLabel()).append("</td>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>").append(productQtys.get(0)).append("</td>")
                                    .append("<td style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>$").append(p.getPrice()).append("</td>")
                                    .append("</tr>");
                        }
                );



//                EMAIL HTML BODY
                String htmlContent = "<html>" +
                        "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0;'>" +
                        "<div style='max-width: 600px; margin: auto; padding: 20px; border: 1px solid #ccc;'>" +
                        "<h2 style='text-align: center; color: #333;'>Thank you for your purchase!</h2>" +
                        "<h3 style='color: #333;'>Order Receipt</h3>" +
                        "<p style='color: #555;'>Hello " + metadata.get("client") + ",</p>" +
                        "<p style='color: #555;'>Thank you for shopping with us. Here are the details of your order:</p>" +
                        "<table style='width: 100%; border-collapse: collapse;'>" +
                        "<thead>" +
                        "<tr>" +
                        "<th style='border-bottom: 1px solid #ddd; padding: 8px; text-align: left;'>Item</th>" +
                        "<th style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>Quantity</th>" +
                        "<th style='border-bottom: 1px solid #ddd; padding: 8px; text-align: right;'>Price</th>" +
                        "</tr>" +
                        "</thead>" +
                        "<tbody>" +
                        productsHtml +
                        "</tbody>" +
                        "<tfoot>" +
                        "<tr>" +
                        "<td style='padding: 8px; text-align: right;' colspan='2'><strong>Total</strong></td>" +
                        "<td style='padding: 8px; text-align: right;'><strong>$" + totalPrices.stream().mapToDouble(Double::doubleValue).sum() + "</strong></td>" +
                        "</tr>" +
                        "</tfoot>" +
                        "</table>" +
                        "<p style='color: #555;'>If you have any questions, feel free to <a href='mailto:ayoubelghazi6@gmail.com'>contact us</a>.</p>" +
                        "<p style='color: #555;'>Best regards,<br>TOPSHOP Team</p>" +
                        "</div>" +
                        "</body>" +
                        "</html>";

                emailSender.sendEmail(
                        clientRepository.findByUsername(metadata.get("client")).get().getEmail(),
                        "Payment Receipt",
                        htmlContent
                        );

                break;
            }
            // ... handle other event types
            default:
                System.out.println("Unhandled event type: " + event.getType());
        }

        return new ResponseEntity<>("Done!", HttpStatus.OK);
    }

}
