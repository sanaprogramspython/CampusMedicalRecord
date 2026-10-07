package com.healthcentre.prescription;

import com.healthcentre.DBConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;

public class PrescriptionPanel extends JPanel {

    private static class ConsultItem {
        final int id;
        final String label;
        ConsultItem(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    private static class MedicineItem {
        final int id, stock;
        final String name;
        final Date expiry;
        MedicineItem(int id, String name, int stock, Date expiry) {
            this.id = id; this.name = name; this.stock = stock; this.expiry = expiry;
        }
        @Override public String toString() { return name + "  (Stock: " + stock + ", Exp: " + expiry + ")"; }
    }

    private final JComboBox<ConsultItem> cbConsultation = new JComboBox<>();
    private final JComboBox<MedicineItem> cbMedicine = new JComboBox<>();
    private final JTextField txtDosage = new JTextField(20);
    private final JSpinner spQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final JLabel lblAllergy = new JLabel("Known allergies: -");
    private final JTextField txtSearch = new JTextField(12);

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Prescription ID", "Consult ID", "Student ID", "Medicine", "Dosage", "Qty Dispensed", "Consult Date"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(tableModel);

    public PrescriptionPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildFormPanel(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        cbConsultation.addActionListener(e -> showAllergies());
        refreshData();
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Dispense Prescription"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        addField(form, g, 0, "Consultation:", cbConsultation);
        addField(form, g, 1, "Medicine:", cbMedicine);
        addField(form, g, 2, "Dosage:", txtDosage);
        addField(form, g, 3, "Quantity:", spQuantity);

        lblAllergy.setForeground(new Color(180, 0, 0));
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2; form.add(lblAllergy, g);

        JButton b1 = new JButton("Dispense"), b2 = new JButton("Clear"), b3 = new JButton("Refresh");
        b1.addActionListener(e -> dispense());
        b2.addActionListener(e -> clearForm());
        b3.addActionListener(e -> refreshData());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(b1); buttons.add(b2); buttons.add(b3);
        g.gridy = 5; g.gridwidth = 1; form.add(buttons, g);
        return form;
    }

    private void addField(JPanel form, GridBagConstraints g, int y, String text, JComponent field) {
        g.gridx = 0; g.gridy = y; g.weightx = 0; form.add(new JLabel(text), g);
        g.gridx = 1; g.weightx = 1; form.add(field, g);
    }

    private JPanel buildTablePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Prescription History"));

        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        search.add(new JLabel("Search by Student ID:"));
        search.add(txtSearch);
        JButton searchBtn = new JButton("Search");
        JButton allBtn = new JButton("Show All");
        searchBtn.addActionListener(e -> loadPrescriptions(txtSearch.getText().trim()));
        allBtn.addActionListener(e -> { txtSearch.setText(""); loadPrescriptions(""); });
        search.add(searchBtn); search.add(allBtn);

        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);

        p.add(search, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    public void refreshData() {
        loadConsultations();
        loadMedicines();
        loadPrescriptions(txtSearch.getText().trim());
        showAllergies();
    }

    private void loadConsultations() {
        String sql = "SELECT c.Consult_ID, p.Student_ID, c.Consult_Date, c.Diagnosis FROM Consultation c JOIN Patient p ON c.Patient_ID = p.Patient_ID ORDER BY c.Consult_ID DESC";
        cbConsultation.removeAllItems();
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String label = "#" + rs.getInt("Consult_ID") + " | " + rs.getString("Student_ID") + " | " + rs.getDate("Consult_Date") + " | " + rs.getString("Diagnosis");
                cbConsultation.addItem(new ConsultItem(rs.getInt("Consult_ID"), label));
            }
        } catch (SQLException ex) { showError("Could not load consultations", ex); }
    }

    private void loadMedicines() {
        String sql = "SELECT Medicine_ID, Medicine_Name, Stock_Quantity, Expiry_Date FROM Medicine ORDER BY Medicine_Name";
        cbMedicine.removeAllItems();
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cbMedicine.addItem(new MedicineItem(rs.getInt("Medicine_ID"), rs.getString("Medicine_Name"), rs.getInt("Stock_Quantity"), rs.getDate("Expiry_Date")));
            }
        } catch (SQLException ex) { showError("Could not load medicines", ex); }
    }

    private void loadPrescriptions(String studentIdFilter) {
        StringBuilder sql = new StringBuilder("SELECT pr.Prescription_ID, pr.Consult_ID, pt.Student_ID, m.Medicine_Name, pr.Dosage, pr.Quantity_Dispensed, c.Consult_Date FROM Prescription pr JOIN Consultation c ON pr.Consult_ID = c.Consult_ID JOIN Patient pt ON c.Patient_ID = pt.Patient_ID JOIN Medicine m ON pr.Medicine_ID = m.Medicine_ID ");
        boolean filter = !studentIdFilter.isEmpty();
        if (filter) sql.append("WHERE pt.Student_ID LIKE ? ");
        sql.append("ORDER BY pr.Prescription_ID DESC");

        tableModel.setRowCount(0);
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (filter) ps.setString(1, "%" + studentIdFilter + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{rs.getInt("Prescription_ID"), rs.getInt("Consult_ID"), rs.getString("Student_ID"), rs.getString("Medicine_Name"), rs.getString("Dosage"), rs.getInt("Quantity_Dispensed"), rs.getDate("Consult_Date")});
                }
            }
        } catch (SQLException ex) { showError("Could not load prescriptions", ex); }
    }

    private String fetchAllergies(int consultId) {
        String sql = "SELECT p.Known_Allergies FROM Consultation c JOIN Patient p ON c.Patient_ID = p.Patient_ID WHERE c.Consult_ID = ?";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, consultId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String a = rs.getString("Known_Allergies");
                    return (a == null || a.isBlank()) ? "None recorded" : a;
                }
            }
        } catch (SQLException ex) { showError("Could not load allergy details", ex); }
        return "None recorded";
    }

    private void showAllergies() {
        ConsultItem c = (ConsultItem) cbConsultation.getSelectedItem();
        lblAllergy.setText("Known allergies: " + (c == null ? "-" : fetchAllergies(c.id)));
    }

    private void dispense() {
        ConsultItem consult = (ConsultItem) cbConsultation.getSelectedItem();
        MedicineItem med = (MedicineItem) cbMedicine.getSelectedItem();
        String dosage = txtDosage.getText().trim();
        int qty = (Integer) spQuantity.getValue();

        if (consult == null || med == null) {
            JOptionPane.showMessageDialog(this, "Select a consultation and a medicine.", "Missing data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (dosage.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the dosage (e.g. 1 tablet twice daily for 5 days).", "Missing data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String allergies = fetchAllergies(consult.id);
        if (allergies.toLowerCase().contains(med.name.toLowerCase())) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "WARNING: Patient has a recorded allergy that matches " + med.name + ".\nKnown allergies: " + allergies + "\n\nDispense anyway?",
                    "Allergy Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String update = "UPDATE Medicine SET Stock_Quantity = Stock_Quantity - ? WHERE Medicine_ID = ? AND Stock_Quantity >= ? AND Expiry_Date >= ?";
            try (PreparedStatement ps = con.prepareStatement(update)) {
                ps.setInt(1, qty);
                ps.setInt(2, med.id);
                ps.setInt(3, qty);
                ps.setDate(4, Date.valueOf(LocalDate.now()));
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    explainFailure(med.id, qty);
                    return;
                }
            }

            String insert = "INSERT INTO Prescription (Consult_ID, Medicine_ID, Dosage, Quantity_Dispensed) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(insert)) {
                ps.setInt(1, consult.id);
                ps.setInt(2, med.id);
                ps.setString(3, dosage);
                ps.setInt(4, qty);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(this, "Dispensed " + qty + " x " + med.name + " successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearForm();
            refreshData();
        } catch (SQLException ex) {
            if (con != null) try { con.rollback(); } catch (SQLException ignored) { }
            showError("Dispensing failed", ex);
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignored) { }
        }
    }

    private void explainFailure(int medicineId, int qty) {
        String sql = "SELECT Medicine_Name, Stock_Quantity, Expiry_Date FROM Medicine WHERE Medicine_ID = ?";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("Medicine_Name");
                    int stock = rs.getInt("Stock_Quantity");
                    Date exp = rs.getDate("Expiry_Date");
                    if (exp != null && exp.toLocalDate().isBefore(LocalDate.now())) {
                        JOptionPane.showMessageDialog(this, name + " expired on " + exp + ". It cannot be dispensed.", "Expired Medicine", JOptionPane.ERROR_MESSAGE);
                    } else if (stock < qty) {
                        JOptionPane.showMessageDialog(this, "Insufficient stock for " + name + ". Available: " + stock + ", requested: " + qty, "Low Stock", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Could not dispense " + name + ".", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
            }
        } catch (SQLException ex) { showError("Could not check medicine status", ex); }
        JOptionPane.showMessageDialog(this, "Medicine not found.", "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void clearForm() {
        txtDosage.setText("");
        spQuantity.setValue(1);
        if (cbConsultation.getItemCount() > 0) cbConsultation.setSelectedIndex(0);
        if (cbMedicine.getItemCount() > 0) cbMedicine.setSelectedIndex(0);
    }

    private void showError(String msg, Exception ex) {
        JOptionPane.showMessageDialog(this, msg + ":\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}