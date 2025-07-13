package com.controller;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.model.User;

@RestController
public class UserController {

    //    @GetMapping("/users")
    // public User scrapeProduction() throws Exception {
    //     Document doc = Jsoup.connect("https://www.me-energies.fr/").get();
    //     //String prodStr = doc.select("your-css-selector").text(); 
    //     double prod = Double.parseDouble(prodStr);

    //     User user = new User();
    //     //user.setProductionKwh(prod);
    //     return user;
    // }
}