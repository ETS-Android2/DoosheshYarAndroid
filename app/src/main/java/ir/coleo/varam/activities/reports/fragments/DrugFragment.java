package ir.coleo.varam.activities.reports.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import ir.coleo.varam.R;
import ir.coleo.varam.activities.DrugSelectionActivity;
import ir.coleo.varam.activities.reports.AddReportActivity;
import ir.coleo.varam.constants.Constants;
import ir.coleo.varam.database.DataBase;
import ir.coleo.varam.database.dao.MyDao;
import ir.coleo.varam.database.models.main.Drug;
import ir.coleo.varam.database.utils.AppExecutors;

public class DrugFragment extends Fragment {

    public DrugFragment() {
    }

    private int[] drugsId = new int[]{R.id.drug_text_one, R.id.drug_text_two,
            R.id.drug_text_three, R.id.drug_text_four, R.id.drug_text_five};
    ArrayList<Pair<Integer, Integer>> setDrugs = new ArrayList<>();
    ArrayList<TextView> drugTextList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drug_add_report, container, false);
        Constants.setImageFront(requireContext(), view.findViewById(R.id.next_icon));
        Constants.setImageBack(requireContext(), view.findViewById(R.id.back_icon));

        for (int i = 0; i < drugsId.length; i++) {
            int finalI = i;
            drugTextList.add(view.findViewById(drugsId[i]));
            drugTextList.get(i).setOnClickListener(view1 -> {
                Intent intent = new Intent(requireContext(), DrugSelectionActivity.class);
                intent.putExtra(Constants.DRUG_TYPE, finalI);
                requireActivity().startActivityForResult(intent, Constants.DRUG_SELECTION);
            });
        }

        view.findViewById(R.id.next_button).setOnClickListener(v -> {
            ((AddReportActivity) requireActivity()).next();
        });
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            ((AddReportActivity) requireActivity()).back();
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void updateText(Pair<Integer, Integer> pair) {
        MyDao dao = DataBase.getInstance(requireContext()).dao();
        AppExecutors.getInstance().diskIO().execute(() -> {
            Drug temp = dao.getDrug(pair.second);
            drugTextList.get(pair.first).setText(temp.name);
            drugTextList.get(pair.first).setTextColor(requireContext().getResources().getColor(R.color.black));
        });
    }

    public void setDrug(int type, int id) {
        for (Pair<Integer, Integer> pair : setDrugs) {
            if (pair.first == type) {
                setDrugs.remove(pair);
            }
        }
        Pair<Integer, Integer> pair = new Pair<>(type, id);
        setDrugs.add(pair);
        updateText(pair);
    }

}