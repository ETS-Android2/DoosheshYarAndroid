package ir.coleo.varam.activities.tabs.report_activites;

import static ir.coleo.varam.R.string.more_info;
import static ir.coleo.varam.R.string.next_visit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import ir.coleo.varam.R;
import ir.coleo.varam.constants.Constants;
import ir.coleo.varam.database.DataBase;
import ir.coleo.varam.database.dao.MyDao;
import ir.coleo.varam.database.models.main.Cow;
import ir.coleo.varam.database.models.main.Drug;
import ir.coleo.varam.database.models.main.Farm;
import ir.coleo.varam.database.models.main.Report;
import ir.coleo.varam.database.models.main.ScoreMethod;
import ir.coleo.varam.database.utils.AppExecutors;
import ir.coleo.varam.models.MyDate;
import saman.zamani.persiandate.PersianDate;

/**
 * صفحه بارگداری یک فایل اکسل در قسمت گزارش‌ها
 */
public class ImportFragment extends Fragment {

    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private ActivityResultLauncher<Intent> fileActivityResultLauncher;
    private TreeSet<Drug> importedDrugs;
    private String finalFarmName;
    private Sheet finalDataTypeSheet;
    private AlertDialog.Builder builder;
    private AlertDialog dialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        builder = new AlertDialog.Builder(Objects.requireNonNull(getContext()));
        dialog = builder.create();
        View view = inflater.inflate(R.layout.fragment_import, container, false);

        ConstraintLayout button = view.findViewById(R.id.import_button);
        button.setOnClickListener(view1 -> showFileChooser());

//        someActivityResultLauncher = registerForActivityResult(
//                new ActivityResultContracts.StartActivityForResult(),
//                result -> {
//                    if (result.getResultCode() == Activity.RESULT_OK) {
//                        // There are no request codes
//                        Intent data = result.getData();
//                        assert data != null;
//                        continueImporting((ScoreMethod) data.getSerializableExtra(Constants.SCORE_METHOD_INTENT), finalDataTypeSheet, finalFarmName, importedDrugs);
//                    }
//                });

        fileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        assert data != null;
                        importFile(data);
                    }
                });

        return view;
    }

    public void showFileChooser() {
        if (Constants.checkPermissionRead(requireContext())) {
            return;
        }

        Intent chooseFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("application/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(chooseFile, Constants.CHOOSE_FILE_REQUEST_CODE);
    }

    @SuppressLint("Range")
    public void importFile(Intent intent) {


        try {
            if (intent == null || intent.getData() == null) {
                return;
            }
            Uri fileUri = intent.getData();
            String fileName = null;
            InputStream stream;
            try {
                if (fileUri == null) {
                    Toast.makeText(requireContext(), "no file selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                stream = requireContext().getContentResolver().openInputStream(fileUri);

                if (Objects.equals(fileUri.getScheme(), "file")) {
                    fileName = fileUri.getLastPathSegment();
                } else {
                    try (Cursor cursor = requireContext().getContentResolver().query(fileUri, new String[]{
                            MediaStore.Images.ImageColumns.DISPLAY_NAME
                    }, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME));
                        }
                    }
                }

            } catch (FileNotFoundException e) {
                Toast.makeText(requireContext(), "file not found", Toast.LENGTH_SHORT).show();
                return;
            }

            assert stream != null;
            assert fileName != null;
            Sheet dataTypeSheet = null;
            String farmName = "imported";
            if (fileName.endsWith(".xls")) {
                POIFSFileSystem myFileSystem = new POIFSFileSystem(stream);
                HSSFWorkbook myWorkBook = new HSSFWorkbook(myFileSystem);
                dataTypeSheet = myWorkBook.getSheetAt(0);
                farmName = fileName.substring(0, fileName.length() - 4);
            } else if (fileName.endsWith(".xlsx")) {
                OPCPackage opcPackage = OPCPackage.open(stream);
                XSSFWorkbook myWorkBook = new XSSFWorkbook(opcPackage);
                dataTypeSheet = myWorkBook.getSheetAt(0);
                farmName = fileName.substring(0, fileName.length() - 5);
            }

            Integer[] headers = {R.string.cow_number, R.string.day, R.string.month, R.string.year,
                    R.string.cartie_number_one, R.string.cartie_number_two, R.string.cartie_number_three,
                    R.string.cartie_number_four, R.string.option_five, R.string.option_six, R.string.option_two,
                    R.string.option_three, R.string.option_eight, R.string.option_four,
                    R.string.option_one, R.string.option_seven_xlsx, R.string.drug_title_1,
                    R.string.drug_title_2, R.string.drug_title_3, R.string.drug_title_4,
                    R.string.drug_title_5, next_visit, more_info, R.string.cure_duration, R.string.chronic, R.string.recurrence};

            //read headers
            int count = 0;
            assert dataTypeSheet != null;
            for (Cell cell : dataTypeSheet.getRow(0)) {
                if (cell == null) {
                    continue;
                }
                if (!cell.getStringCellValue().equals(getString(headers[count]))) {
                    Toast.makeText(requireContext(), "expected : " + getString(headers[count])
                            + " find : " + cell.getStringCellValue(), Toast.LENGTH_LONG).show();
                    return;
                }
                count++;
            }
            importedDrugs = new TreeSet<>();
            TreeSet<String> importedScoreName = new TreeSet<>();
            {
                Iterator<Row> rows = dataTypeSheet.iterator();
                rows.next();
                while (rows.hasNext()) {
                    Row row = rows.next();
                    for (int i = 4; i < 8; i++) {
                        Cell cell = row.getCell(i);
                        if (cell == null) {
                            continue;
                        }
                        if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                            String temp = cell.getStringCellValue();
                            if (!temp.isEmpty() && !temp.equals("*"))
                                importedScoreName.add(temp);
                        } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                            String temp = "" + cell.getNumericCellValue();
                            if (!temp.isEmpty() && !temp.equals("*"))
                                importedScoreName.add(temp);
                        }
                    }
                    for (int i = 16; i < 21; i++) {
                        Cell cell = row.getCell(i);
                        if (cell == null) {
                            continue;
                        }
                        if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                            String temp = cell.getStringCellValue().trim().toLowerCase();
                            if (!temp.isEmpty()) {
                                Drug drug = new Drug();
                                drug.type = i - 16;
                                drug.name = temp;
                                importedDrugs.add(drug);
                            }
                        }
                    }
                }
                // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
                finalDataTypeSheet = dataTypeSheet;
                finalFarmName = farmName;

                ScoreMethod scoreMethod = new ScoreMethod();
                scoreMethod.scoresCount = importedScoreName.size();
                scoreMethod.scoresNameList = new ArrayList<>(importedScoreName);
//                Log.e("TAG", "importFile: " + scoreMethod.scoresNameList.size());
                continueImporting(scoreMethod, finalDataTypeSheet, finalFarmName, importedDrugs);
            }

//            Intent createScoreMethodIntent = new Intent(requireActivity(), CreateScoreMethod.class);
//            createScoreMethodIntent.putExtra(Constants.SCORE_METHOD_INTENT_MODE, "IMPORT");
//            createScoreMethodIntent.putExtra(Constants.SCORE_METHOD_INTENT_DATA, new ArrayList<>(importedScoreName));
//            someActivityResultLauncher.launch(createScoreMethodIntent);

        } catch (IOException | InvalidFormatException e) {
            Toast.makeText(requireContext(), "reading error", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }


    }

//    public void showProgressDialog(boolean isShow) {
//        if (isShow){
//            int llPadding = 30;
//            LinearLayout ll = new LinearLayout(getContext());
//            ll.setOrientation(LinearLayout.HORIZONTAL);
//            ll.setPadding(llPadding, llPadding, llPadding, llPadding);
//            ll.setGravity(Gravity.CENTER);
//            LinearLayout.LayoutParams llParam = new LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT);
//            llParam.gravity = Gravity.CENTER;
//            ll.setLayoutParams(llParam);
//
//            ProgressBar progressBar = new ProgressBar(getContext());
//            progressBar.setIndeterminate(true);
//            progressBar.setPadding(0, 0, llPadding, 0);
//            progressBar.setLayoutParams(llParam);
//
//            llParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
//                    ViewGroup.LayoutParams.WRAP_CONTENT);
//            llParam.gravity = Gravity.CENTER;
//            TextView tvText = new TextView(getContext());
//            tvText.setText("لطفا صبر کنید...");
//            tvText.setTextColor(Color.parseColor("#000000"));
//            tvText.setTextSize(20);
//            tvText.setLayoutParams(llParam);
//
//            ll.addView(progressBar);
//            ll.addView(tvText);
//
//            builder.setCancelable(true);
//            builder.setView(ll);
//
//            dialog.show();
//            Window window = dialog.getWindow();
//            if (window != null) {
//                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
//                layoutParams.copyFrom(dialog.getWindow().getAttributes());
//                layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
//                layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
//                dialog.getWindow().setAttributes(layoutParams);
//            }
//        }else{
//            if (dialog != null) {
//                dialog.dismiss();
//            }
//        }
//    }

    public void continueImporting(ScoreMethod scoreMethod, Sheet finalDataTypeSheet, String finalFarmName, TreeSet<Drug> importedDrugs) {
//        showProgressDialog(true);
        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.importing, Toast.LENGTH_SHORT).show());
        MyDao dao = DataBase.getInstance(requireContext()).dao();
        AppExecutors.getInstance().diskIO().execute(() -> {
            scoreMethod.id = dao.insertGetId(scoreMethod);
            Farm farm = new Farm();
            farm.name = finalFarmName;
            farm.favorite = false;
            farm.birthCount = 0;
            farm.showerCount = 0;
            farm.bedType = "";
            farm.showerUnitCount = 0;
            farm.showerPitCount = 0;
            farm.scoreMethodId = scoreMethod.id;
            farm.id = (int) dao.insertGetId(farm);

            HashSet<Integer> cowNumbers = new HashSet<>();
            Hashtable<Integer, Integer> cowNumberCowId = new Hashtable<>();
            ArrayList<Cow> cows = new ArrayList<>();
            ArrayList<Report> reports = new ArrayList<>();
            List<Drug> drugList = dao.getAllDrug();
            ArrayList<Drug> newDrugs = new ArrayList<>();
            main:
            for (Drug newDrug : importedDrugs) {
                for (Drug drug : drugList) {
                    if (drug.compareTo(newDrug) == 0) {
                        continue main;
                    }
                }
                newDrugs.add(newDrug);
            }
            for (Drug drug : newDrugs) {
                dao.insert(drug);
            }
            drugList = dao.getAllDrug();


            Iterator<Row> rows = finalDataTypeSheet.iterator();
            rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                Cell cell = row.getCell(0);
                if (cell == null) {
                    Log.i("IMPORT", "continueImporting: one");
                    continue;
                }
                int cowNumber;
                if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    cowNumber = Integer.parseInt(cell.getStringCellValue());
                } else {
                    cowNumber = (int) cell.getNumericCellValue();
                }
                cowNumbers.add(cowNumber);
            }
            for (Integer cowNumber : cowNumbers) {
                Cow cow = new Cow(cowNumber, false, farm.id);
                cow.setId((int) dao.insertGetId(cow));
                cowNumberCowId.put(cowNumber, cow.getId());
                cows.add(cow);
            }

            rows = finalDataTypeSheet.iterator();
            rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                Report report = new Report();

                Cell cell = row.getCell(0);
                if (cell == null) {
                    Log.i("IMPORT", "continueImporting: one");
                    continue;
                }
                if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    report.cowId = Integer.parseInt(cell.getStringCellValue());
                } else {
                    report.cowId = (int) cell.getNumericCellValue();
                }
                report.cowId = cowNumberCowId.get(report.cowId);
                report.scoreMethodId = scoreMethod.id;
                cowNumbers.add(report.cowId);

                int day, month, year;
                cell = row.getCell(3);
                if (cell == null) {
                    Log.i("IMPORT", "continueImporting: 2");
                    continue;
                }
                if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    String temp = cell.getStringCellValue();
                    if (temp != null && !temp.isEmpty()) {
                        year = Integer.parseInt(temp);
                    } else {
                        Log.i("IMPORT", "continueImporting: 3");
//                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
                        continue;
                    }
                } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                    year = (int) cell.getNumericCellValue();
                } else {
                    Log.i("IMPORT", "continueImporting: 4");
//                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
                    continue;
                }

                cell = row.getCell(2);
                if (cell == null) {
                    Log.i("IMPORT", "continueImporting: 5");
                    continue;
                }
                if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    String temp = cell.getStringCellValue();
                    if (temp != null && !temp.isEmpty()) {
                        month = Integer.parseInt(temp);
                    } else {
                        Log.i("IMPORT", "continueImporting: 6");
//                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
                        continue;
                    }
                } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                    month = (int) cell.getNumericCellValue();
                } else {
                    Log.i("IMPORT", "continueImporting: 7");
                    continue;
//                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
//                    return;
                }

                cell = row.getCell(1);
                if (cell == null) {
                    Log.i("IMPORT", "continueImporting: 8");
                    continue;
                }
                if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    String temp = cell.getStringCellValue();
                    if (temp != null && !temp.isEmpty()) {
                        day = Integer.parseInt(temp);
                    } else {
                        Log.i("IMPORT", "continueImporting: 9");
                        continue;
//                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
//                        return;
                    }

                } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                    day = (int) cell.getNumericCellValue();
                } else {
                    Log.i("IMPORT", "continueImporting: 10");
                    continue;
//                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "read error", Toast.LENGTH_LONG).show());
//                    return;
                }


                if (Constants.getDefaultLanguage(requireContext()).equals("fa")) {
                    PersianDate pdate = new PersianDate();
                    int[] dateArray = pdate.toGregorian(year, month, day);
                    report.visit = new MyDate(dateArray[2], dateArray[1], dateArray[0]);
                } else {
                    report.visit = new MyDate(day, month, year);
                }

                score_loop:
                for (int i = 4; i < 8; i++) {
                    cell = row.getCell(i);
                    if (cell == null) {
                        continue;
                    }
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String temp = cell.getStringCellValue();
                        if (temp != null && !temp.isEmpty()) {
                            if (temp.equals("*")) {
                                report.areaNumber = i - 3;
                                break;
                            }
                            for (int j = 0; j < scoreMethod.scoresNameList.size(); j++) {
                                String scoreString = scoreMethod.scoresNameList.get(j);
                                if (scoreString.equals(temp)) {
                                    report.score = j;
                                    report.areaNumber = i - 3;
                                    break score_loop;
                                }
                            }
                            Log.i("IMPORT", "continueImporting: 12");
//                            continue;
//                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "score and area number error", Toast.LENGTH_SHORT).show());
                            return;
                        }
                    } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                        String temp = String.valueOf(cell.getNumericCellValue());
                        if (!temp.isEmpty()) {
                            if (temp.equals("*")) {
                                report.areaNumber = i - 3;
                                break;
                            }
                            for (int j = 0; j < scoreMethod.scoresNameList.size(); j++) {
                                String scoreString = scoreMethod.scoresNameList.get(j);
                                if (scoreString.equals(temp)) {
                                    report.score = j;
                                    report.areaNumber = i - 3;
                                    break score_loop;
                                }
                            }
                            Log.i("IMPORT", "continueImporting: 13");
//                            continue;
//                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "score and area number error", Toast.LENGTH_SHORT).show());
                            return;
                        }
                    }
                }

                for (int i = 10; i < 15; i++) {
                    cell = row.getCell(i);
                    if (cell == null) {
                        continue;
                    }
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String temp = cell.getStringCellValue();
                        if (temp != null && !temp.isEmpty() && temp.equals("*")) {
                            report.cartieState = i - 10;
                            break;
                        }
                    }
                }

                cell = row.getCell(8);
                if (cell != null)
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String star = cell.getStringCellValue();
                        if (star != null && !star.isEmpty() && star.equals("*")) {
                            report.sardalme = true;
                        }
                    } else {
                        report.sardalme = false;
                    }
                else {
                    report.sardalme = false;
                }

                cell = row.getCell(9);
                if (cell != null)
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String star = cell.getStringCellValue();
                        if (star != null && !star.isEmpty() && star.equals("*")) {
                            report.khoni = true;
                        }
                    } else {
                        report.khoni = false;
                    }
                else {
                    report.khoni = false;
                }

                cell = row.getCell(15);
                if (cell != null)
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String star = cell.getStringCellValue();
                        if (star != null && !star.isEmpty() && star.equals("*")) {
                            report.kor = true;
                        }
                    } else {
                        report.kor = false;
                    }
                else {
                    report.kor = false;
                }

                for (int i = 16; i < 21; i++) {
                    cell = row.getCell(i);
                    if (cell == null) {
                        continue;
                    }
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String drugName = cell.getStringCellValue();
                        if (drugName == null || drugName.isEmpty()) {
                            continue;
                        }
                        for (Drug drug : drugList) {
                            if (drug.type == i - 16 && drug.name.equals(drugName)) {
                                switch (drug.type) {
                                    case 0: {
                                        report.pomadeId = drug.id;
                                        break;
                                    }
                                    case 1: {
                                        report.antibioticId = drug.id;
                                        break;
                                    }
                                    case 2: {
                                        report.serumId = drug.id;
                                        break;
                                    }
                                    case 3: {
                                        report.cureId = drug.id;
                                        break;
                                    }
                                    case 4: {
                                        report.antiInflammatoryId = drug.id;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }


                Cell nextVisitCell = row.getCell(21);
                if (nextVisitCell != null)
                    if (nextVisitCell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String temp = nextVisitCell.getStringCellValue();
                        if (temp != null && !temp.isEmpty()) {
                            String[] date = temp.split("/");
                            if (Constants.getDefaultLanguage(requireContext()).equals("fa")) {
                                PersianDate pdate = new PersianDate();
                                int[] dateArray = pdate.toGregorian(Integer.parseInt(date[0]),
                                        Integer.parseInt(date[1]),
                                        Integer.parseInt(date[2]));
                                report.nextVisit = new MyDate(dateArray[2], dateArray[1], dateArray[0]);
                            } else {
                                report.nextVisit = new MyDate(Integer.parseInt(date[2]),
                                        Integer.parseInt(date[1]),
                                        Integer.parseInt(date[0]));
                            }
                        }
                    }

                Cell moreInfo = row.getCell(22);
                if (moreInfo != null) {
                    if (moreInfo.getCellType() == Cell.CELL_TYPE_STRING) {
                        String temp = moreInfo.getStringCellValue();
                        if (temp != null && !temp.isEmpty())
                            report.description = temp;
                    }
                }

                Cell cureDuration = row.getCell(23);
                if (cureDuration != null) {
                    if (cureDuration.getCellType() == Cell.CELL_TYPE_STRING) {
                        String temp = cureDuration.getStringCellValue();
                        if (temp != null && !temp.isEmpty())
                            report.cureDuration = Long.parseLong(temp);
                    } else if (cureDuration.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                        report.cureDuration = (long) cureDuration.getNumericCellValue();
                    }
                }

                cell = row.getCell(24);
                if (cell != null)
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String star = cell.getStringCellValue();
                        if (star != null && !star.isEmpty() && star.equals("*")) {
                            report.chronic = true;
                        }
                    } else {
                        report.chronic = false;
                    }
                else {
                    report.chronic = false;
                }

                cell = row.getCell(25);
                if (cell != null)
                    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        String star = cell.getStringCellValue();
                        if (star != null && !star.isEmpty() && star.equals("*")) {
                            report.recurrence = true;
                        }
                    } else {
                        report.recurrence = false;
                    }
                else {
                    report.recurrence = false;
                }

                reports.add(report);
            }
            dao.insert(reports);

            requireActivity().runOnUiThread(()-> {
//                showProgressDialog(false);
                Toast.makeText(requireContext(), reports.size() + " گزارش ایمپورت شد.", Toast.LENGTH_LONG).show();
            });
        });


    }

    @Override
    public void onResume() {
        super.onResume();
    }
}