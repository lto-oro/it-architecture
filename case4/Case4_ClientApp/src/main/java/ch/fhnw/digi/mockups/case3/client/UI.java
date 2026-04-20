package ch.fhnw.digi.mockups.case3.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ch.fhnw.digi.mockups.case3.JobAssignmentMessage;
import ch.fhnw.digi.mockups.case3.JobMessage;

@SuppressWarnings("serial")
@Component
public class UI extends JFrame {

	private static final String ALL_JOB_TYPES = "Alle Job-Arten";

	private static final Color APP_BACKGROUND = new Color(238, 242, 247);
	private static final Color CARD_BACKGROUND = Color.WHITE;
	private static final Color HEADER_BACKGROUND = new Color(25, 44, 73);
	private static final Color HEADER_TEXT = new Color(247, 250, 252);
	private static final Color PRIMARY = new Color(16, 110, 190);
	private static final Color PRIMARY_DARK = new Color(11, 77, 133);
	private static final Color ACCENT = new Color(230, 125, 45);
	private static final Color DISABLED_BUTTON = new Color(206, 211, 218);
	private static final Color DISABLED_TEXT = new Color(108, 117, 125);
	private static final Color TEXT_PRIMARY = new Color(28, 37, 46);
	private static final Color TEXT_MUTED = new Color(97, 109, 126);
	private static final Color BORDER = new Color(214, 222, 232);
	private static final Color LIST_BACKGROUND = new Color(250, 252, 255);
	private static final Color LIST_SELECTION = new Color(221, 235, 250);
	private static final Color URGENT = new Color(196, 55, 55);
	private static final Color URGENT_DARK = new Color(142, 30, 30);
	private static final Color URGENT_BADGE_BG = new Color(253, 235, 235);
	private static final Color URGENT_LIST_BACKGROUND = new Color(253, 245, 244);

	private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
	private static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 13);
	private static final Font FONT_SECTION_TITLE = new Font("Segoe UI Semibold", Font.PLAIN, 16);
	private static final Font FONT_LABEL = new Font("Segoe UI Semibold", Font.PLAIN, 12);
	private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
	private static final Font FONT_BADGE = new Font("Segoe UI Semibold", Font.PLAIN, 12);
	private static final Font FONT_BUTTON = new Font("Segoe UI Semibold", Font.PLAIN, 13);

	@Autowired
	private MessageSender messageSender;

	private JList<JobMessage> m_list_jobs;
	private DefaultListModel<JobMessage> m_list_jobs_model;

	private JList<JobMessage> m_list_urgent;
	private DefaultListModel<JobMessage> m_list_urgent_model;

	private JList<String> m_list_assignments;
	private DefaultListModel<String> m_list_assignments_model;

	private JButton m_btn_requestJob;
	private JButton m_btn_applyFilter;
	private JButton m_btn_resetFilter;
	private JTextField m_txt_regionFilter;
	private JComboBox<String> m_cmb_jobTypeFilter;
	private JLabel m_lbl_openJobsCount;
	private JLabel m_lbl_urgentJobsCount;
	private JLabel m_lbl_assignmentsCount;
	private JLabel m_lbl_filterStatus;
	private final List<JobMessage> m_all_jobs = new ArrayList<JobMessage>();

	@PostConstruct
	void init() {

		m_list_jobs_model = new DefaultListModel<JobMessage>();
		m_list_jobs = new JList<JobMessage>(m_list_jobs_model);
		m_list_jobs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		m_list_jobs.setCellRenderer(new JobCellRenderer());
		m_list_jobs.setBackground(LIST_BACKGROUND);
		m_list_jobs.setFixedCellHeight(-1);
		m_list_jobs.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				updateActionState();
			}
		});

		m_list_urgent_model = new DefaultListModel<JobMessage>();
		m_list_urgent = new JList<JobMessage>(m_list_urgent_model);
		m_list_urgent.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		m_list_urgent.setCellRenderer(new UrgentJobCellRenderer());
		m_list_urgent.setBackground(URGENT_LIST_BACKGROUND);
		m_list_urgent.setFixedCellHeight(-1);

		m_list_assignments_model = new DefaultListModel<String>();
		m_list_assignments = new JList<String>(m_list_assignments_model);
		m_list_assignments.setCellRenderer(new AssignmentCellRenderer());
		m_list_assignments.setBackground(LIST_BACKGROUND);
		m_list_assignments.setFixedCellHeight(-1);

		m_btn_requestJob = new JButton("Job anfordern");
		m_btn_requestJob.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				acceptSelectedJob();
			}
		});
		stylePrimaryButton(m_btn_requestJob);

		m_txt_regionFilter = new JTextField(16);
		m_txt_regionFilter.setFont(FONT_BODY);
		m_txt_regionFilter.setBorder(new LineBorder(BORDER, 1, true));
		m_txt_regionFilter.setPreferredSize(new Dimension(190, 34));
		m_txt_regionFilter.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applyRegionFilter();
			}
		});

		m_cmb_jobTypeFilter = new JComboBox<String>(new String[] { ALL_JOB_TYPES, "Maintanence", "Repair" });
		m_cmb_jobTypeFilter.setFont(FONT_BODY);
		m_cmb_jobTypeFilter.setPreferredSize(new Dimension(190, 34));
		m_cmb_jobTypeFilter.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applyRegionFilter();
			}
		});

		m_btn_applyFilter = new JButton("Filter anwenden");
		m_btn_applyFilter.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applyRegionFilter();
			}
		});
		styleSecondaryButton(m_btn_applyFilter);

		m_btn_resetFilter = new JButton("Zurücksetzen");
		m_btn_resetFilter.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				m_txt_regionFilter.setText("");
				m_cmb_jobTypeFilter.setSelectedItem(ALL_JOB_TYPES);
				applyRegionFilter();
			}
		});
		styleGhostButton(m_btn_resetFilter);

		m_lbl_openJobsCount = createBadgeLabel();
		m_lbl_urgentJobsCount = createUrgentBadgeLabel();
		m_lbl_assignmentsCount = createBadgeLabel();
		m_lbl_filterStatus = new JLabel();
		m_lbl_filterStatus.setFont(FONT_SUBTITLE);
		m_lbl_filterStatus.setForeground(HEADER_TEXT);

		JPanel rootPanel = new JPanel(new BorderLayout(0, 18));
		rootPanel.setBackground(APP_BACKGROUND);
		rootPanel.setBorder(new EmptyBorder(18, 18, 18, 18));

		rootPanel.add(createHeaderPanel(), BorderLayout.NORTH);
		rootPanel.add(createContentPanel(), BorderLayout.CENTER);
		rootPanel.add(createActionBar(), BorderLayout.SOUTH);

		setTitle("Dispatch Console");
		setContentPane(rootPanel);
		setSize(1320, 720);
		setMinimumSize(new Dimension(1180, 640));
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		updateMetrics();
		updateActionState();
		setVisible(true);
	}

	private JPanel createHeaderPanel() {
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(HEADER_BACKGROUND);
		headerPanel.setBorder(new EmptyBorder(18, 20, 18, 20));

		JPanel titlePanel = new JPanel();
		titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
		titlePanel.setOpaque(false);

		JLabel title = new JLabel("Dispatch Console");
		title.setFont(FONT_TITLE);
		title.setForeground(HEADER_TEXT);

		JLabel subtitle = new JLabel("Offene Aufträge überwachen, filtern und direkt bei der Disposition anfordern.");
		subtitle.setFont(FONT_SUBTITLE);
		subtitle.setForeground(new Color(211, 222, 236));

		titlePanel.add(title);
		titlePanel.add(subtitle);

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
		statusPanel.setOpaque(false);

		JLabel statusLabel = new JLabel("Aktiver Filter");
		statusLabel.setFont(FONT_LABEL);
		statusLabel.setForeground(new Color(181, 198, 221));
		statusLabel.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);

		m_lbl_filterStatus.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);

		statusPanel.add(statusLabel);
		statusPanel.add(m_lbl_filterStatus);

		headerPanel.add(titlePanel, BorderLayout.WEST);
		headerPanel.add(statusPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JPanel createContentPanel() {
		JPanel contentPanel = new JPanel(new GridLayout(1, 3, 18, 0));
		contentPanel.setOpaque(false);
		contentPanel.add(createJobsCard());
		contentPanel.add(createUrgentJobsCard());
		contentPanel.add(createAssignmentsCard());
		return contentPanel;
	}

	private JPanel createUrgentJobsCard() {
		JPanel card = createCardPanel();
		card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(URGENT, 1, true),
				new EmptyBorder(18, 18, 18, 18)));

		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 14, 0));

		JPanel titleBlock = new JPanel();
		titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
		titleBlock.setOpaque(false);

		JLabel title = new JLabel("Dringende Jobs");
		title.setFont(FONT_SECTION_TITLE);
		title.setForeground(URGENT_DARK);

		JLabel subtitle = new JLabel("Reparaturaufträge der Dispositionsabteilung (group4.jobs.urgent).");
		subtitle.setFont(FONT_SUBTITLE);
		subtitle.setForeground(TEXT_MUTED);

		titleBlock.add(title);
		titleBlock.add(subtitle);

		header.add(titleBlock, BorderLayout.WEST);
		header.add(m_lbl_urgentJobsCount, BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		card.add(createListScrollPane(m_list_urgent), BorderLayout.CENTER);
		return card;
	}

	private JPanel createJobsCard() {
		JPanel card = createCardPanel();
		card.add(createJobsCardHeader(), BorderLayout.NORTH);
		card.add(createListScrollPane(m_list_jobs), BorderLayout.CENTER);
		return card;
	}

	private JPanel createAssignmentsCard() {
		JPanel card = createCardPanel();

		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 14, 0));

		JPanel titleBlock = new JPanel();
		titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
		titleBlock.setOpaque(false);

		JLabel title = new JLabel("Zugewiesene Jobs");
		title.setFont(FONT_SECTION_TITLE);
		title.setForeground(TEXT_PRIMARY);

		JLabel subtitle = new JLabel("Rückmeldungen der Disposition für dieses Team.");
		subtitle.setFont(FONT_SUBTITLE);
		subtitle.setForeground(TEXT_MUTED);

		titleBlock.add(title);
		titleBlock.add(subtitle);

		header.add(titleBlock, BorderLayout.WEST);
		header.add(m_lbl_assignmentsCount, BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		card.add(createListScrollPane(m_list_assignments), BorderLayout.CENTER);
		return card;
	}

	private JPanel createJobsCardHeader() {
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBorder(new EmptyBorder(0, 0, 14, 0));

		JPanel titleRow = new JPanel(new BorderLayout(8, 0));
		titleRow.setOpaque(false);

		JPanel titleBlock = new JPanel();
		titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
		titleBlock.setOpaque(false);

		JLabel title = new JLabel("Offene Jobs");
		title.setFont(FONT_SECTION_TITLE);
		title.setForeground(TEXT_PRIMARY);

		JLabel subtitle = new JLabel("Regionen filtern und den passenden Auftrag direkt anfordern.");
		subtitle.setFont(FONT_SUBTITLE);
		subtitle.setForeground(TEXT_MUTED);

		titleBlock.add(title);
		titleBlock.add(subtitle);

		titleRow.add(titleBlock, BorderLayout.WEST);
		titleRow.add(m_lbl_openJobsCount, BorderLayout.EAST);

		JPanel filterPanel = new JPanel();
		filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
		filterPanel.setOpaque(false);
		filterPanel.setBorder(new EmptyBorder(12, 0, 0, 0));

		JPanel regionFilterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		regionFilterRow.setOpaque(false);

		JLabel regionFilterLabel = new JLabel("Region");
		regionFilterLabel.setFont(FONT_LABEL);
		regionFilterLabel.setForeground(TEXT_MUTED);

		regionFilterRow.add(regionFilterLabel);
		regionFilterRow.add(m_txt_regionFilter);
		regionFilterRow.add(m_btn_applyFilter);
		regionFilterRow.add(m_btn_resetFilter);

		JPanel jobTypeFilterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
		jobTypeFilterRow.setOpaque(false);

		JLabel jobTypeFilterLabel = new JLabel("Job-Art");
		jobTypeFilterLabel.setFont(FONT_LABEL);
		jobTypeFilterLabel.setForeground(TEXT_MUTED);

		jobTypeFilterRow.add(jobTypeFilterLabel);
		jobTypeFilterRow.add(m_cmb_jobTypeFilter);

		filterPanel.add(regionFilterRow);
		filterPanel.add(jobTypeFilterRow);

		header.add(titleRow);
		header.add(filterPanel);
		return header;
	}

	private JPanel createActionBar() {
		JPanel actionBar = new JPanel(new BorderLayout(12, 0));
		actionBar.setBackground(CARD_BACKGROUND);
		actionBar.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true),
				new EmptyBorder(14, 16, 14, 16)));

		JPanel hintPanel = new JPanel();
		hintPanel.setLayout(new BoxLayout(hintPanel, BoxLayout.Y_AXIS));
		hintPanel.setOpaque(false);

		JLabel title = new JLabel("Nächster Schritt");
		title.setFont(FONT_LABEL);
		title.setForeground(TEXT_MUTED);

		JLabel hint = new JLabel("Wähle einen sichtbaren Job aus und sende danach die Anforderung an die Disposition.");
		hint.setFont(FONT_BODY);
		hint.setForeground(TEXT_PRIMARY);

		hintPanel.add(title);
		hintPanel.add(hint);

		actionBar.add(hintPanel, BorderLayout.CENTER);
		actionBar.add(m_btn_requestJob, BorderLayout.EAST);

		return actionBar;
	}

	protected void acceptSelectedJob() {
		JobMessage m = m_list_jobs.getSelectedValue();
		if (m == null) {
			return;
		}

		messageSender.requestJobFromDispo(m);
	}

	public void addJobToList(JobMessage j) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized (m_all_jobs) {
					m_all_jobs.add(0, j);
				}
				refreshVisibleJobs();
			}
		});
	}

	public void assignJob(JobAssignmentMessage c) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized (m_all_jobs) {
					for (int x = 0; x < m_all_jobs.size(); ++x) {
						if (m_all_jobs.get(x).getJobnumber().equals(c.getJobnumber())) {
							m_all_jobs.remove(x);
							break;
						}
					}
				}
				refreshVisibleJobs();

				synchronized (m_list_assignments_model) {
					m_list_assignments_model.add(0,
							"Job " + c.getJobnumber() + " wurde an \"" + c.getAssignedEmployee() + "\" vergeben");
				}
				updateMetrics();
			}
		});
	}

	private void applyRegionFilter() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				refreshVisibleJobs();
			}
		});
	}

	private void refreshVisibleJobs() {
		String selectedJobNumber = null;
		JobMessage selectedJob = m_list_jobs.getSelectedValue();
		if (selectedJob != null) {
			selectedJobNumber = selectedJob.getJobnumber();
		}

		synchronized (m_list_jobs_model) {
			m_list_jobs_model.clear();

			synchronized (m_all_jobs) {
				for (JobMessage job : m_all_jobs) {
					if (matchesSelectedRegion(job)) {
						m_list_jobs_model.addElement(job);
					}
				}
			}
		}

		synchronized (m_list_urgent_model) {
			m_list_urgent_model.clear();

			synchronized (m_all_jobs) {
				for (JobMessage job : m_all_jobs) {
					if (isUrgent(job)) {
						m_list_urgent_model.addElement(job);
					}
				}
			}
		}

		restoreSelection(selectedJobNumber);
		updateMetrics();
		updateActionState();
	}

	private boolean isUrgent(JobMessage job) {
		return job.getType() != null && "Repair".equals(job.getType().name());
	}

	private boolean matchesSelectedRegion(JobMessage job) {
		String selectedRegion = m_txt_regionFilter.getText();
		if (selectedRegion == null || selectedRegion.trim().isEmpty()) {
			return matchesSelectedJobType(job);
		}

		if (job.getRegion() == null) {
			return false;
		}

		return job.getRegion().toLowerCase().contains(selectedRegion.trim().toLowerCase()) && matchesSelectedJobType(job);
	}

	private boolean matchesSelectedJobType(JobMessage job) {
		Object selectedItem = m_cmb_jobTypeFilter.getSelectedItem();
		if (selectedItem == null || ALL_JOB_TYPES.equals(selectedItem.toString())) {
			return true;
		}

		if (job.getType() == null) {
			return false;
		}

		return selectedItem.toString().equals(job.getType().name());
	}

	private void restoreSelection(String selectedJobNumber) {
		if (selectedJobNumber == null) {
			return;
		}

		for (int i = 0; i < m_list_jobs_model.size(); i++) {
			JobMessage job = m_list_jobs_model.get(i);
			if (selectedJobNumber.equals(job.getJobnumber())) {
				m_list_jobs.setSelectedIndex(i);
				m_list_jobs.ensureIndexIsVisible(i);
				break;
			}
		}
	}

	private void updateMetrics() {
		m_lbl_openJobsCount.setText(m_list_jobs_model.size() + " sichtbar");
		m_lbl_urgentJobsCount.setText(m_list_urgent_model.size() + " dringend");
		m_lbl_assignmentsCount.setText(m_list_assignments_model.size() + " verarbeitet");

		String activeFilter = m_txt_regionFilter.getText();
		String selectedJobType = m_cmb_jobTypeFilter.getSelectedItem() == null ? ALL_JOB_TYPES
				: m_cmb_jobTypeFilter.getSelectedItem().toString();

		String regionStatus = activeFilter == null || activeFilter.trim().isEmpty() ? "Alle Regionen"
				: "Region enthält: " + activeFilter.trim();
		String jobTypeStatus = ALL_JOB_TYPES.equals(selectedJobType) ? "alle Job-Arten"
				: "Job-Art: " + selectedJobType;

		if ("Alle Regionen".equals(regionStatus)) {
			m_lbl_filterStatus.setText(jobTypeStatus);
		} else {
			m_lbl_filterStatus.setText(regionStatus + " | " + jobTypeStatus);
		}
	}

	private void updateActionState() {
		boolean hasSelection = m_list_jobs.getSelectedValue() != null;
		m_btn_requestJob.setEnabled(hasSelection);
		m_btn_requestJob.setBackground(hasSelection ? ACCENT : DISABLED_BUTTON);
		m_btn_requestJob.setForeground(hasSelection ? Color.BLACK : DISABLED_TEXT);
	}

	private JPanel createCardPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CARD_BACKGROUND);
		panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true),
				new EmptyBorder(18, 18, 18, 18)));
		return panel;
	}

	private JScrollPane createListScrollPane(JList<?> list) {
		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.getViewport().setBackground(LIST_BACKGROUND);
		scrollPane.setBorder(new LineBorder(BORDER, 1, true));
		return scrollPane;
	}

	private JLabel createBadgeLabel() {
		JLabel label = new JLabel();
		label.setFont(FONT_BADGE);
		label.setForeground(PRIMARY_DARK);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(new Color(233, 242, 252));
		label.setBorder(new EmptyBorder(8, 12, 8, 12));
		return label;
	}

	private JLabel createUrgentBadgeLabel() {
		JLabel label = new JLabel();
		label.setFont(FONT_BADGE);
		label.setForeground(URGENT_DARK);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(URGENT_BADGE_BG);
		label.setBorder(new EmptyBorder(8, 12, 8, 12));
		return label;
	}

	private void stylePrimaryButton(JButton button) {
		button.setFont(FONT_BUTTON);
		button.setForeground(Color.BLACK);
		button.setBackground(ACCENT);
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(11, 18, 11, 18));
	}

	private void styleSecondaryButton(JButton button) {
		button.setFont(FONT_BUTTON);
		button.setForeground(Color.WHITE);
		button.setBackground(PRIMARY);
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(9, 14, 9, 14));
	}

	private void styleGhostButton(JButton button) {
		button.setFont(FONT_BUTTON);
		button.setForeground(PRIMARY_DARK);
		button.setBackground(new Color(237, 243, 249));
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(9, 14, 9, 14));
	}

	private static String safeValue(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "-";
		}
		return escapeHtml(value);
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class JobCellRenderer extends DefaultListCellRenderer {
		@Override
		public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			JobMessage job = (JobMessage) value;

			boolean urgent = job.getType() != null && "Repair".equals(job.getType().name());

			label.setFont(FONT_BODY);
			label.setBorder(new EmptyBorder(10, 12, 10, 12));
			label.setOpaque(true);
			label.setBackground(isSelected ? LIST_SELECTION : LIST_BACKGROUND);
			label.setForeground(TEXT_PRIMARY);

			String urgentBadge = urgent
					? "<span style='background-color:#c43737;color:#ffffff;font-weight:bold;padding:1px 6px;"
							+ "font-size:10px;'>DRINGEND</span>&nbsp;"
					: "";

			String html = "<html><div style='width:430px;'>"
					+ urgentBadge
					+ "<span style='font-size:13px;font-weight:bold;color:#1c252e;'>Job "
					+ safeValue(job.getJobnumber()) + "</span>"
					+ "<span style='font-size:12px;color:#6f7d8c;'>  |  "
					+ safeValue(job.getType() == null ? null : job.getType().name()) + "</span><br/>"
					+ "<span style='font-size:12px;color:#344050;'>" + safeValue(job.getDescription()) + "</span><br/>"
					+ "<span style='font-size:11px;color:#617087;'>Region: " + safeValue(job.getRegion())
					+ "  |  Geplant: " + safeValue(job.getScheduledDateTime()) + "</span>" + "</div></html>";

			label.setText(html);
			return label;
		}
	}

	private static class UrgentJobCellRenderer extends DefaultListCellRenderer {
		@Override
		public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			JobMessage job = (JobMessage) value;

			label.setFont(FONT_BODY);
			label.setBorder(new EmptyBorder(10, 12, 10, 12));
			label.setOpaque(true);
			label.setBackground(isSelected ? LIST_SELECTION : URGENT_LIST_BACKGROUND);
			label.setForeground(TEXT_PRIMARY);

			String html = "<html><div style='width:420px;'>"
					+ "<span style='background-color:#c43737;color:#ffffff;font-weight:bold;padding:1px 6px;"
					+ "font-size:10px;'>REPAIR</span>&nbsp;"
					+ "<span style='font-size:13px;font-weight:bold;color:#8e1e1e;'>Job "
					+ safeValue(job.getJobnumber()) + "</span><br/>"
					+ "<span style='font-size:12px;color:#344050;'>" + safeValue(job.getDescription()) + "</span><br/>"
					+ "<span style='font-size:11px;color:#617087;'>Region: " + safeValue(job.getRegion())
					+ "  |  Geplant: " + safeValue(job.getScheduledDateTime()) + "</span>" + "</div></html>";

			label.setText(html);
			return label;
		}
	}

	private static class AssignmentCellRenderer extends DefaultListCellRenderer {
		@Override
		public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			label.setFont(FONT_BODY);
			label.setBorder(new EmptyBorder(10, 12, 10, 12));
			label.setOpaque(true);
			label.setBackground(isSelected ? LIST_SELECTION : LIST_BACKGROUND);
			label.setForeground(TEXT_PRIMARY);

			String html = "<html><div style='width:390px;'>"
					+ "<span style='font-size:12px;color:#1c252e;font-weight:bold;'>Vergabe bestätigt</span><br/>"
					+ "<span style='font-size:12px;color:#4b5968;'>" + safeValue((String) value) + "</span>"
					+ "</div></html>";

			label.setText(html);
			return label;
		}
	}
}
