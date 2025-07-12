package com.controller;

// import your repository classes here, for example:
import com.repository.ConsumptionRepository;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.model.*;
import com.repository.*;


import java.util.concurrent.TimeUnit;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.FileInputStream;
import java.util.Iterator;

import org.springframework.beans.factory.annotation.Autowired;

// Add Apache POI imports for Excel parsing
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController
public class ConsumptionController {
    @Autowired
private ConsumptionRepository consumptionRepository;
    

    // Helper method to start Chrome with download disabled (headless mode)
private WebDriver startChromeWithDownloadDisabled() {
    String downloadFilepath = System.getProperty("java.io.tmpdir") + "enedis_download/";
    File dir = new File(downloadFilepath);
    if (!dir.exists()) dir.mkdirs();

    HashMap<String, Object> chromePrefs = new HashMap<>();
    chromePrefs.put("profile.default_content_settings.popups", 0);
    chromePrefs.put("download.default_directory", downloadFilepath);
    chromePrefs.put("download.prompt_for_download", false);
    chromePrefs.put("download.directory_upgrade", true);
    chromePrefs.put("safebrowsing.enabled", true);

    ChromeOptions options = new ChromeOptions();
    options.setExperimentalOption("prefs", chromePrefs);
    options.addArguments("--headless");
    options.addArguments("--disable-gpu");
    options.addArguments("--window-size=1920,1080");

    // Setup chromedriver
    WebDriverManager.chromedriver().setup();

    // Return ONE driver
    return new ChromeDriver(options);
}


    @GetMapping("/scrape-consumption")
    public String scrapeConsumption(
            @RequestParam String email,
            @RequestParam String pw,
            @RequestParam(defaultValue = "01/01/2025") String fromDate,
            @RequestParam(defaultValue = "11/07/2025") String toDate
    ) throws Exception {

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        WebDriver driver = new ChromeDriver(options);

        try {
            // 1. Go to login page
            driver.get("https://mon-compte-particulier.enedis.fr");

            // 2. Wait for redirect and load
            TimeUnit.SECONDS.sleep(5);

            // 3. Switch to login iframe if exists, or find login form
            // This part must be adapted to the actual HTML structure of the login form.
            WebElement emailField = driver.findElement(By.id("Identifiant")); // 👈 Update this ID if needed
            WebElement passwordField = driver.findElement(By.id("Mot de passe")); // 👈 Update this ID if needed

            emailField.sendKeys(email);
            passwordField.sendKeys(pw);

            WebElement loginBtn = driver.findElement(By.xpath("//button[contains(text(), 'Se connecter')]"));
            loginBtn.click();

            // 4. Wait for authentication to complete
            TimeUnit.SECONDS.sleep(10); // adjust if needed

            // 5. Now go to consumption data page
            driver.get("https://mon-compte-particulier.enedis.fr/visualiser-vos-mesures-consommation");
            TimeUnit.SECONDS.sleep(5);

            // 6. Fill date range
            WebElement startDate = driver.findElement(By.xpath("//input[@placeholder='Date de début']"));
            WebElement endDate = driver.findElement(By.xpath("//input[@placeholder='Date de fin']"));
            startDate.clear();
            startDate.sendKeys(fromDate);
            endDate.clear();
            endDate.sendKeys(toDate);

            // 7. Click "Visualiser"
            WebElement visualiser = driver.findElement(By.xpath("//button[contains(text(), 'Visualiser')]"));
            visualiser.click();
            TimeUnit.SECONDS.sleep(10);

            // 8. Click "Télécharger"
            WebElement downloadButton = driver.findElement(By.xpath("//button[contains(text(), 'Télécharger')]"));
            downloadButton.click();
            TimeUnit.SECONDS.sleep(10);

            return "✅ Download started.";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error: " + e.getMessage();
        } finally {
            driver.quit();
        }
    }
    @GetMapping("/consumption")
public ResponseEntity<String> fetchAndSaveConsumption(@RequestBody LoginRequest loginRequest) throws Exception {
    WebDriver driver = startChromeWithDownloadDisabled();
    String email = loginRequest.getEmail();
    String password = loginRequest.getPassword();
    try {
        
        loginToEnedis(driver, email, password);
        File excelFile = exportExcelFileFromEnedis(driver);
        List<Consumption> consumptions = parseExcelToConsumptionList(excelFile);
        consumptionRepository.saveAll(consumptions);

        // ✅ Delete the file after parsing
        if (excelFile.exists()) {
            excelFile.delete();
        }

        return ResponseEntity.ok("✅ Consumption data saved");
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError().body("❌ Error: " + e.getMessage());
    } finally {
        driver.quit();
    }
}


    // Implement the missing loginToEnedis method
    private void loginToEnedis(WebDriver driver, String email, String password) throws InterruptedException {
        driver.get("https://mon-compte-particulier.enedis.fr");
        TimeUnit.SECONDS.sleep(5);
        WebElement emailField = driver.findElement(By.id("Identifiant")); // Update if needed
        WebElement passwordField = driver.findElement(By.id("Mot de passe")); // Update if needed
        emailField.sendKeys(email);
        passwordField.sendKeys(password);
        WebElement loginBtn = driver.findElement(By.xpath("//button[contains(text(), 'Se connecter')]"));
        loginBtn.click();
        TimeUnit.SECONDS.sleep(10); // Wait for authentication
    }

    // Implement the missing exportExcelFileFromEnedis method
   private File exportExcelFileFromEnedis(WebDriver driver) throws InterruptedException {
    String downloadPath = System.getProperty("java.io.tmpdir") + "enedis_download/";
    File dir = new File(downloadPath);
    long timeout = System.currentTimeMillis() + 30_000; // 30s timeout

    // Navigate to the page
    driver.get("https://mon-compte-particulier.enedis.fr/visualiser-vos-mesures-consommation");
    TimeUnit.SECONDS.sleep(5);

    // Click the download button
    WebElement downloadButton = driver.findElement(By.xpath("//button[contains(text(), 'Télécharger')]"));
    downloadButton.click();

    // Wait for the file to appear in the temp folder
    File downloadedFile = null;
    while (System.currentTimeMillis() < timeout) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xlsx"));
        if (files != null && files.length > 0) {
            downloadedFile = files[0];
            break;
        }
        Thread.sleep(1000);
    }

    if (downloadedFile == null) {
        throw new RuntimeException("Download timed out or failed.");
    }

    return downloadedFile;
}




private List<Consumption> parseExcelToConsumptionList(File excelFile) {
    List<Consumption> consumptions = new ArrayList<>();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy"); // adjust if needed

    try (FileInputStream fis = new FileInputStream(excelFile);
         Workbook workbook = new XSSFWorkbook(fis)) {

        Sheet sheet = workbook.getSheetAt(1); // Sheet 2 (index 1)
        Sheet sheetP = workbook.getSheetAt(2); // Sheet 3 (index 2)

        Iterator<Row> rowIterator = sheet.iterator();

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            if (row.getRowNum() <= 14) {
                continue; // skip header rows
            }

            Cell dateCell = row.getCell(1); // Column B
            Cell consumptionCell = row.getCell(2); // Column C

            if (dateCell == null || consumptionCell == null) {
                continue; // skip incomplete rows
            }

            LocalDate date;
            if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                date = dateCell.getLocalDateTimeCellValue().toLocalDate();
            } else if (dateCell.getCellType() == CellType.STRING) {
                date = LocalDate.parse(dateCell.getStringCellValue(), dateFormatter);
            } else {
                continue; // skip invalid date cells
            }

            double consumptionValue = consumptionCell.getNumericCellValue();

            // Get matching row in SheetP
            Row rowP = sheetP.getRow(row.getRowNum());
            if (rowP == null) {
                continue; // skip if no matching row
            }
            Cell productionCell = rowP.getCell(2); // Column C
            if (productionCell == null) {
                continue; // skip if missing
            }
            double productionValue = productionCell.getNumericCellValue();

            System.out.printf("Date: %s, Consumption: %.2f, Production: %.2f%n",
                    date, consumptionValue, productionValue);
            //User dummyUser = userRepository.findById(1L).orElse(null);
            
            Consumption consumption = new Consumption();
            //consumption.setUser(dummyUser);
            consumption.setDate(date);
            consumption.setConsumptionKwh(consumptionValue);
            consumption.setProductionKwh(productionValue);

            consumptions.add(consumption);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return consumptions;
}


}
