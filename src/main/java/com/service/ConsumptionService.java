package com.service;

import com.model.Consumption;
import com.repository.ConsumptionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ConsumptionService {

    @Autowired
    private ConsumptionRepository consumptionRepository;

    private WebDriver createWebDriverWithDownload(String downloadDir) {
        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir);
        prefs.put("download.prompt_for_download", false);
        prefs.put("safebrowsing.enabled", true);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--headless", "--disable-gpu");
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver(options);
    }

    public String fetchAndSaveConsumption(String email, String password, String fromDate, String toDate) {
        String downloadDir = System.getProperty("java.io.tmpdir") + "enedis_download/";
        new File(downloadDir).mkdirs();
        WebDriver driver = createWebDriverWithDownload(downloadDir);

        try {
            login(driver, email, password);
            downloadExcel(driver, fromDate, toDate);
            File excelFile = waitForDownload(downloadDir);

            List<Consumption> consumptions = parseExcel(excelFile);
            consumptionRepository.saveAll(consumptions);

            Files.deleteIfExists(excelFile.toPath());
            return "✅ Consumption data saved successfully.";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error: " + e.getMessage();
        } finally {
            driver.quit();
        }
    }

    private void login(WebDriver driver, String email, String password) throws InterruptedException {
        driver.get("https://mon-compte-particulier.enedis.fr");
        TimeUnit.SECONDS.sleep(5);
        driver.findElement(By.id("Identifiant")).sendKeys(email);
        driver.findElement(By.id("Mot de passe")).sendKeys(password);
        driver.findElement(By.xpath("//button[contains(text(), 'Se connecter')]")).click();
        TimeUnit.SECONDS.sleep(10);
    }

    private void downloadExcel(WebDriver driver, String fromDate, String toDate) throws InterruptedException {
        driver.get("https://mon-compte-particulier.enedis.fr/visualiser-vos-mesures-consommation");
        TimeUnit.SECONDS.sleep(5);

        WebElement start = driver.findElement(By.xpath("//input[@placeholder='Date de début']"));
        WebElement end = driver.findElement(By.xpath("//input[@placeholder='Date de fin']"));
        start.clear(); start.sendKeys(fromDate);
        end.clear(); end.sendKeys(toDate);

        driver.findElement(By.xpath("//button[contains(text(), 'Visualiser')]")).click();
        TimeUnit.SECONDS.sleep(5);

        driver.findElement(By.xpath("//button[contains(text(), 'Télécharger')]")).click();
        TimeUnit.SECONDS.sleep(5);
    }

    private File waitForDownload(String dir) throws InterruptedException {
        long timeout = System.currentTimeMillis() + 30000; // 30 seconds
        File downloadedFile = null;
        while (System.currentTimeMillis() < timeout) {
            File[] files = new File(dir).listFiles((d, name) -> name.endsWith(".xlsx"));
            if (files != null && files.length > 0) {
                downloadedFile = files[0];
                break;
            }
            Thread.sleep(1000);
        }
        if (downloadedFile == null) throw new RuntimeException("Download timed out.");
        return downloadedFile;
    }

    private List<Consumption> parseExcel(File file) throws Exception {
        List<Consumption> list = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(1);
            Sheet sheetP = wb.getSheetAt(2);

            for (Row row : sheet) {
                if (row.getRowNum() <= 14) continue;
                Cell dateCell = row.getCell(1);
                Cell consCell = row.getCell(2);
                if (dateCell == null || consCell == null) continue;

                LocalDate date;
                if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                    date = dateCell.getLocalDateTimeCellValue().toLocalDate();
                } else if (dateCell.getCellType() == CellType.STRING) {
                    date = LocalDate.parse(dateCell.getStringCellValue(), formatter);
                } else continue;

                double consValue = consCell.getNumericCellValue();

                Row prodRow = sheetP.getRow(row.getRowNum());
                if (prodRow == null) continue;
                Cell prodCell = prodRow.getCell(2);
                if (prodCell == null) continue;
                double prodValue = prodCell.getNumericCellValue();

                Consumption c = new Consumption();
                c.setDate(date);
                c.setConsumptionKwh(consValue);
                c.setProductionKwh(prodValue);

                list.add(c);
            }
        }
        return list;
    }
}
