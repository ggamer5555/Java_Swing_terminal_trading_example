import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.Cursor;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FundModelSwingClient {
    private static final Path DEFAULT_WORKSPACE_ROOT = Paths.get("D:\\xgb pyhton");
    private static final Path DEFAULT_DATA_ROOT = Paths.get("F:\\data_dump");
    private static final Path APP_DIR = detectAppDir();
    private static final Path WORKSPACE_ROOT = Files.exists(APP_DIR.resolve("history_trade.sqlite")) ? APP_DIR : DEFAULT_WORKSPACE_ROOT;
    private static final Path DATA_ROOT = Files.isDirectory(APP_DIR.resolve("data_dump").resolve("daily_bin").resolve("spot")) ? APP_DIR.resolve("data_dump") : DEFAULT_DATA_ROOT;
    private static final int RECORD_SIZE = 128;
    private static final int MAX_BARS = 100;
    private static final double EPS = 1e-12;
    private static final long MIN_VALID_MS = 946_684_800_000L;
    private static final long FUTURE_TOLERANCE_MS = 24L * 3600L * 1000L;

    private static final Color BG = Color.WHITE;
    private static final Color PANEL = new Color(0xFAFAFA);
    private static final Color INK = Color.BLACK;
    private static final Color MUTED = new Color(0x4B5563);
    private static final Color GRID = new Color(0xD1D5DB);
    private static final Color HEADER = new Color(0xF3F4F6);
    private static final Color STRIPE = new Color(0xF8FAFC);
    private static final Color ACCENT = new Color(0x111827);
    private static final Color GREEN = new Color(0x059669);
    private static final Color RED = new Color(0xDC2626);
    private static final Color BLUE = new Color(0x2563EB);

    private FundModelSwingClient() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--check".equalsIgnoreCase(args[0])) {
            try {
                ChartResult r = new SpotDataService().read(args.length > 1 ? args[1] : "BTCUSDT", args.length > 2 ? args[2] : "1m");
                System.out.println(r.message);
            } catch (Exception ex) {
                System.err.println(cleanError(ex));
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && "--dbcheck".equalsIgnoreCase(args[0])) {
            try {
                DataService d = new DataService();
                PnlResult p = d.pnl("");
                System.out.println("trade_summary_rows=" + d.tradeSummary().size());
                System.out.println("open_trade_rows=" + d.openTrades().size());
                System.out.println("market_rules_rows=" + d.marketRulesCombined().size());
                System.out.println("closed_pnl_rows=" + p.closedRows.size());
                System.out.println("equity_points=" + p.curve.size());
            } catch (Exception ex) {
                System.err.println(cleanError(ex));
                System.exit(1);
            }
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
                new AppFrame().setVisible(true);
            }
        });
    }

    private interface Refreshable {
        void refreshFromConfig();
    }

    private static final class AppFrame extends JFrame {
        private final SpotDataService spot = new SpotDataService();
        private final DataService data = new DataService();
        private final List<Refreshable> refreshables = new ArrayList<Refreshable>();
        private final JLabel zoomLabel = new JLabel("100%");
        private float zoom = 1.0f;

        AppFrame() {
            super("FundModelSwingClient");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1050, 720));
            setSize(1320, 820);
            setLocationByPlatform(true);

            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(BG);
            tabs.setForeground(INK);
            ChartsPanel charts = new ChartsPanel(spot);
            TradesPanel trades = new TradesPanel(data);
            PnlPanel pnl = new PnlPanel(data);
            SymbolActivePanel active = new SymbolActivePanel();
            refreshables.add(charts);
            refreshables.add(trades);
            refreshables.add(pnl);
            refreshables.add(active);
            tabs.addTab("Charts", charts);
            tabs.addTab("Trades", trades);
            tabs.addTab("PnL", pnl);
            tabs.addTab("Symbol Active", active);
            final boolean[] loaded = new boolean[]{true, false, false, false};
            tabs.addChangeListener(e -> {
                int idx = tabs.getSelectedIndex();
                if (idx >= 0 && idx < loaded.length && !loaded[idx]) {
                    loaded[idx] = true;
                    refreshables.get(idx).refreshFromConfig();
                }
            });

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG);
            JPanel zoomBar = toolbar();
            zoomBar.add(label("Zoom"));
            JButton out = button("-");
            JButton reset = button("100%");
            JButton in = button("+");
            zoomLabel.setForeground(MUTED);
            zoomBar.add(out);
            zoomBar.add(reset);
            zoomBar.add(in);
            zoomBar.add(zoomLabel);
            out.addActionListener(e -> setZoom(zoom - 0.1f));
            reset.addActionListener(e -> setZoom(1.0f));
            in.addActionListener(e -> setZoom(zoom + 0.1f));
            root.add(zoomBar, BorderLayout.NORTH);
            root.add(tabs, BorderLayout.CENTER);
            setContentPane(root);
            applyUiScale(root, zoom);
        }

        private void setZoom(float value) {
            zoom = Math.max(0.70f, Math.min(1.80f, value));
            zoomLabel.setText(Math.round(zoom * 100.0f) + "%");
            applyUiScale(getContentPane(), zoom);
            revalidate();
            repaint();
        }
    }

    private static final class ChartsPanel extends JPanel implements Refreshable {
        private final SpotDataService service;
        private final JComboBox<String> timeframeBox = new JComboBox<String>(new String[]{"1m", "5m", "15m", "30m", "1h", "2h", "4h", "D"});
        private final JSpinner barsSpinner = new JSpinner(new SpinnerNumberModel(100, 1, MAX_BARS, 1));
        private final JTextField symbolSearch = new JTextField(16);
        private final JLabel status = new JLabel(" ");
        private final JLabel liveLight = new JLabel("\u25CF");
        private final ChartCanvas chart = new ChartCanvas();
        private final SymbolStatusModel symbolModel = new SymbolStatusModel();
        private final JTable symbolTable = new JTable(symbolModel);
        private final TableRowSorter<SymbolStatusModel> symbolSorter = new TableRowSorter<SymbolStatusModel>(symbolModel);
        private final javax.swing.Timer chartTimer;
        private final javax.swing.Timer statusTimer;
        private String selectedSymbol = "BTCUSDT";
        private boolean loading;
        private boolean symbolListLoading;
        private boolean statusBatchLoading;
        private int nextStatusRow;

        ChartsPanel(SpotDataService service) {
            super(new BorderLayout(8, 8));
            this.service = service;
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setBackground(BG);
            JPanel top = toolbar();
            top.add(label("Timeframe"));
            styleCombo(timeframeBox);
            top.add(timeframeBox);
            top.add(label("Bars"));
            styleSpinner(barsSpinner);
            top.add(barsSpinner);
            liveLight.setForeground(RED);
            liveLight.setFont(liveLight.getFont().deriveFont(Font.BOLD, 18f));
            top.add(label("Live"));
            top.add(liveLight);
            status.setForeground(MUTED);
            top.add(status);
            add(top, BorderLayout.NORTH);

            JScrollPane scroll = new JScrollPane(chart);
            scroll.setBorder(BorderFactory.createLineBorder(GRID));
            scroll.getViewport().setBackground(BG);
            add(splitHorizontal(scroll, symbolSidebar(), 0.78), BorderLayout.CENTER);
            timeframeBox.addActionListener(e -> load());
            barsSpinner.addChangeListener(e -> load());
            symbolModel.setSymbols(Collections.singletonList(selectedSymbol));
            selectSymbolRow(selectedSymbol);
            load();
            loadSymbolListInBackground();
            chartTimer = new javax.swing.Timer(5000, e -> load());
            statusTimer = new javax.swing.Timer(900, e -> updateNextStatusBatch());
            chartTimer.setRepeats(true);
            statusTimer.setRepeats(true);
            chartTimer.start();
            statusTimer.start();
        }

        @Override
        public void refreshFromConfig() {
            loadSymbolListInBackground();
            load();
        }

        private JPanel symbolSidebar() {
            JPanel side = new JPanel(new BorderLayout(6, 6));
            side.setBackground(BG);
            side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xCBD5E1)), new EmptyBorder(10, 10, 10, 10)));
            JLabel title = label("Spot Symbols");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            JPanel top = new JPanel(new BorderLayout(6, 6));
            top.setOpaque(false);
            top.add(title, BorderLayout.NORTH);
            styleTextField(symbolSearch);
            top.add(symbolSearch, BorderLayout.CENTER);
            side.add(top, BorderLayout.NORTH);

            symbolTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            symbolTable.setRowSorter(symbolSorter);
            symbolTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            styleTable(symbolTable);
            symbolTable.getColumnModel().getColumn(0).setPreferredWidth(46);
            symbolTable.getColumnModel().getColumn(1).setPreferredWidth(105);
            symbolTable.getColumnModel().getColumn(2).setPreferredWidth(94);
            symbolTable.getColumnModel().getColumn(3).setPreferredWidth(78);
            symbolTable.setDefaultRenderer(Object.class, new SymbolStatusRenderer());
            side.add(wrapTable(symbolTable), BorderLayout.CENTER);
            symbolSearch.getDocument().addDocumentListener(new SimpleDocumentListener() {
                @Override
                void changed() {
                    applySymbolSearch();
                }
            });
            symbolTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int view = symbolTable.getSelectedRow();
                    if (view >= 0) {
                        int modelRow = symbolTable.convertRowIndexToModel(view);
                        String sym = symbolModel.symbolAt(modelRow);
                        if (!sym.equals(selectedSymbol)) {
                            selectedSymbol = sym;
                            load();
                        }
                    }
                }
            });
            return side;
        }

        private void applySymbolSearch() {
            final String q = symbolSearch.getText().trim().toLowerCase(Locale.ROOT);
            symbolSorter.setRowFilter(q.isEmpty() ? null : new javax.swing.RowFilter<SymbolStatusModel, Integer>() {
                @Override
                public boolean include(Entry<? extends SymbolStatusModel, ? extends Integer> entry) {
                    return stringValue(entry.getValue(1)).toLowerCase(Locale.ROOT).contains(q);
                }
            });
        }

        private void loadSymbolListInBackground() {
            if (symbolListLoading) {
                return;
            }
            symbolListLoading = true;
            status.setText("Loading chart. Filling symbol list in background...");
            new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() {
                    return service.listSpotSymbols();
                }

                @Override
                protected void done() {
                    try {
                        List<String> symbols = get();
                        symbolModel.setSymbols(symbols);
                        applySymbolSearch();
                        if (!symbols.isEmpty() && !symbolModel.contains(selectedSymbol)) {
                            selectedSymbol = symbols.contains("BTCUSDT") ? "BTCUSDT" : symbols.get(0);
                            load();
                        }
                        selectSymbolRow(selectedSymbol);
                        updateNextStatusBatch();
                    } catch (Exception ex) {
                        status.setText(cleanError(ex));
                    } finally {
                        symbolListLoading = false;
                    }
                }
            }.execute();
        }

        private void updateNextStatusBatch() {
            if (statusBatchLoading || symbolModel.getRowCount() == 0) {
                return;
            }
            final ArrayList<String> batch = new ArrayList<String>();
            int total = symbolModel.getRowCount();
            int limit = Math.min(8, total);
            for (int i = 0; i < limit; i++) {
                int row = (nextStatusRow + i) % total;
                batch.add(symbolModel.symbolAt(row));
            }
            nextStatusRow = (nextStatusRow + limit) % total;
            statusBatchLoading = true;
            new SwingWorker<List<SymbolStatus>, Void>() {
                @Override
                protected List<SymbolStatus> doInBackground() {
                    ArrayList<SymbolStatus> out = new ArrayList<SymbolStatus>();
                    for (String symbol : batch) {
                        out.add(service.symbolStatus(symbol));
                    }
                    return out;
                }

                @Override
                protected void done() {
                    try {
                        for (SymbolStatus s : get()) {
                            symbolModel.updateStatus(s);
                        }
                        updateLiveLight();
                    } catch (Exception ignored) {
                    } finally {
                        statusBatchLoading = false;
                    }
                }
            }.execute();
        }

        private void selectSymbolRow(String symbol) {
            if (symbol == null || symbol.isEmpty()) {
                return;
            }
            for (int i = 0; i < symbolModel.getRowCount(); i++) {
                if (symbol.equals(symbolModel.symbolAt(i))) {
                    int view = symbolTable.convertRowIndexToView(i);
                    if (view >= 0) {
                        symbolTable.getSelectionModel().setSelectionInterval(view, view);
                        symbolTable.scrollRectToVisible(symbolTable.getCellRect(view, 0, true));
                    }
                    return;
                }
            }
        }

        private void load() {
            if (loading || selectedSymbol.isEmpty()) {
                return;
            }
            loading = true;
            final String symbol = selectedSymbol;
            final String tf = timeframeBox.getSelectedItem().toString();
            final int bars = ((Number) barsSpinner.getValue()).intValue();
            status.setText("Loading " + symbol + " " + tf + "...");
            new SwingWorker<ChartResult, Void>() {
                @Override
                protected ChartResult doInBackground() throws Exception {
                    ChartResult r = service.read(symbol, tf);
                    if (r.candles.size() > bars) {
                        r.candles = new ArrayList<Candle>(r.candles.subList(r.candles.size() - bars, r.candles.size()));
                    }
                    r.message = "Plotted " + r.candles.size() + " spot bars.";
                    return r;
                }

                @Override
                protected void done() {
                    try {
                        ChartResult r = get();
                        chart.setResult(r);
                        status.setText(r.message);
                        updateLiveLight();
                    } catch (Exception ex) {
                        String msg = cleanError(ex);
                        chart.setResult(ChartResult.message(msg));
                        status.setText(msg);
                        liveLight.setForeground(RED);
                    } finally {
                        loading = false;
                    }
                }
            }.execute();
        }

        private void updateLiveLight() {
            SymbolStatus s = symbolModel.find(selectedSymbol);
            liveLight.setForeground(s != null && s.live ? GREEN : RED);
        }
    }

    private static final class TradesPanel extends JPanel implements Refreshable {
        private final DataService service;
        private final TableSection summary = new TableSection("Table 1 - Sum target_qty per symbol");
        private final TableSection open = new TableSection("Table 2 - Open trades");
        private final TableSection rules = new TableSection("Table 3 - Market rules");
        private final JLabel status = new JLabel(" ");

        TradesPanel(DataService service) {
            super(new BorderLayout(8, 8));
            this.service = service;
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setBackground(BG);
            JPanel top = toolbar();
            JButton refresh = button("Refresh Tables");
            refresh.addActionListener(e -> refreshFromConfig());
            top.add(refresh);
            status.setForeground(MUTED);
            top.add(status);
            add(top, BorderLayout.NORTH);

            JSplitPane lower = splitVertical(open, rules, 0.48);
            JSplitPane all = splitVertical(summary, lower, 0.34);
            add(all, BorderLayout.CENTER);
            status.setText("Select this tab or press Refresh Tables to load.");
        }

        @Override
        public void refreshFromConfig() {
            status.setText("Loading...");
            new SwingWorker<List<List<Map<String, Object>>>, Void>() {
                @Override
                protected List<List<Map<String, Object>>> doInBackground() {
                    List<List<Map<String, Object>>> out = new ArrayList<List<Map<String, Object>>>();
                    out.add(service.tradeSummary());
                    out.add(service.openTrades());
                    out.add(service.marketRulesCombined());
                    return out;
                }

                @Override
                protected void done() {
                    try {
                        List<List<Map<String, Object>>> rows = get();
                        summary.setRows(rows.get(0));
                        open.setRows(rows.get(1));
                        rules.setRows(rows.get(2));
                        status.setText("Loaded " + rows.get(0).size() + " / " + rows.get(1).size() + " / " + rows.get(2).size() + " rows.");
                    } catch (Exception ex) {
                        status.setText(cleanError(ex));
                    }
                }
            }.execute();
        }
    }

    private static final class PnlPanel extends JPanel implements Refreshable {
        private final DataService service;
        private final JTextField search = new JTextField(18);
        private final JLabel status = new JLabel(" ");
        private final TableSection stats = new TableSection("Stats", true);
        private final TableSection closed = new TableSection("Closed trades");
        private final TableSection open = new TableSection("Open trades");
        private final EquityCanvas curve = new EquityCanvas();

        PnlPanel(DataService service) {
            super(new BorderLayout(8, 8));
            this.service = service;
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setBackground(BG);
            JPanel top = toolbar();
            top.add(label("Search"));
            styleTextField(search);
            top.add(search);
            JButton refresh = button("Refresh PnL");
            refresh.addActionListener(e -> refreshFromConfig());
            top.add(refresh);
            status.setForeground(MUTED);
            top.add(status);
            add(top, BorderLayout.NORTH);

            JPanel upper = new JPanel(new BorderLayout(10, 10));
            upper.setOpaque(false);
            upper.add(splitHorizontal(stats, curve, 0.28), BorderLayout.CENTER);
            JSplitPane lower = splitVertical(closed, open, 0.56);
            JSplitPane split = splitVertical(upper, lower, 0.38);
            add(split, BorderLayout.CENTER);
            search.getDocument().addDocumentListener(new SimpleDocumentListener() {
                @Override
                void changed() {
                    refreshFromConfig();
                }
            });
            status.setText("Select this tab or press Refresh PnL to load.");
        }

        @Override
        public void refreshFromConfig() {
            final String q = search.getText().trim().toLowerCase(Locale.ROOT);
            status.setText("Loading...");
            new SwingWorker<PnlResult, Void>() {
                @Override
                protected PnlResult doInBackground() {
                    return service.pnl(q);
                }

                @Override
                protected void done() {
                    try {
                        PnlResult r = get();
                        stats.setRows(statsRows(r.stats));
                        closed.setRows(r.closedRows);
                        open.setRows(r.openRows);
                        curve.setPoints(r.curve);
                        status.setText(r.closedRows.size() + " closed, " + r.openRows.size() + " open.");
                    } catch (Exception ex) {
                        status.setText(cleanError(ex));
                    }
                }
            }.execute();
        }
    }

    private static final class SymbolActivePanel extends JPanel implements Refreshable {
        private final JTextField addSymbol = new JTextField(16);
        private final JLabel path = new JLabel(" ");
        private final JCheckBox allDisabled = new JCheckBox("all_symbols_disabled");
        private final SymbolActiveModel model = new SymbolActiveModel();
        private final JTable table = new JTable(model);

        SymbolActivePanel() {
            super(new BorderLayout(8, 8));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setBackground(BG);
            JPanel top = toolbar();
            allDisabled.setBackground(BG);
            top.add(allDisabled);
            top.add(label("Symbol"));
            styleTextField(addSymbol);
            top.add(addSymbol);
            JButton add = button("Add");
            JButton save = button("Save");
            JButton reload = button("Reload");
            top.add(add);
            top.add(save);
            top.add(reload);
            path.setForeground(MUTED);
            top.add(path);
            add(top, BorderLayout.NORTH);
            styleTable(table);
            add(wrapTable(table), BorderLayout.CENTER);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            add.addActionListener(e -> addSymbol());
            save.addActionListener(e -> save());
            reload.addActionListener(e -> refreshFromConfig());
            path.setText("Select this tab or press Reload to load.");
        }

        @Override
        public void refreshFromConfig() {
            SymbolActive active = SymbolActive.read(symbolActivePath());
            allDisabled.setSelected(active.allDisabled);
            model.setRows(active.symbols);
            path.setText(symbolActivePath().toString());
        }

        private void addSymbol() {
            String s = addSymbol.getText().trim();
            if (!s.isEmpty()) {
                model.put(s, Boolean.FALSE);
                addSymbol.setText("");
            }
        }

        private void save() {
            SymbolActive active = new SymbolActive();
            active.allDisabled = allDisabled.isSelected();
            active.symbols.putAll(model.asMap());
            try {
                active.write(symbolActivePath());
                path.setText("Saved: " + symbolActivePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static final class SpotDataService {
        List<String> listSpotSymbols() {
            TreeSet<String> out = new TreeSet<String>();
            Path dir = DATA_ROOT.resolve("daily_bin").resolve("spot");
            if (!Files.isDirectory(dir)) {
                return new ArrayList<String>();
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    if (Files.isDirectory(p)) {
                        out.add(p.getFileName().toString());
                    }
                }
            } catch (IOException ignored) {
            }
            return new ArrayList<String>(out);
        }

        List<SymbolStatus> symbolStatuses() {
            ArrayList<SymbolStatus> out = new ArrayList<SymbolStatus>();
            for (String symbol : listSpotSymbols()) {
                out.add(symbolStatus(symbol));
            }
            return out;
        }

        SymbolStatus symbolStatus(String symbol) {
            List<Path> files = listFiles(symbol);
            if (files.isEmpty()) {
                return new SymbolStatus(symbol, false, null, 0.0, "no file", "");
            }
            Path latest = files.get(files.size() - 1);
            long modified = 0L;
            long size = 0L;
            try {
                modified = Files.getLastModifiedTime(latest).toMillis();
                size = Files.size(latest);
            } catch (IOException ignored) {
            }
            Candle candle = null;
            try {
                candle = readLatest(latest);
            } catch (IOException ignored) {
            }
            long ageMs = modified > 0L ? System.currentTimeMillis() - modified : Long.MAX_VALUE;
            boolean recent = ageMs >= 0L && ageMs <= 120_000L;
            String age = modified <= 0L ? "-" : ageText(ageMs);
            String sig = latest.getFileName().toString() + ":" + size + ":" + modified;
            return new SymbolStatus(symbol, recent, candle == null ? null : candle.timeMs, candle == null ? 0.0 : candle.close, age, sig);
        }

        ChartResult read(String symbol, String timeframe) throws IOException {
            int frameMinutes = frameMinutes(timeframe);
            int neededOneMinuteRows = Math.max(MAX_BARS * frameMinutes + frameMinutes, MAX_BARS);
            List<Path> files = listFiles(symbol);
            ArrayList<Candle> rows = new ArrayList<Candle>();
            int remaining = neededOneMinuteRows;
            for (int i = files.size() - 1; i >= 0 && remaining > 0; i--) {
                Path f = files.get(i);
                long size = Files.size(f);
                if (size <= 0 || size % RECORD_SIZE != 0) {
                    continue;
                }
                long total = size / RECORD_SIZE;
                int take = (int) Math.min((long) remaining, total);
                readChunk(f, (total - take) * RECORD_SIZE, take, rows);
                remaining -= take;
            }
            Collections.sort(rows, new Comparator<Candle>() {
                @Override
                public int compare(Candle a, Candle b) {
                    return Long.compare(a.timeMs, b.timeMs);
                }
            });
            rows = dedupe(rows);
            List<Candle> aggregated = aggregate(rows, frameMinutes);
            if (aggregated.size() > MAX_BARS) {
                aggregated = new ArrayList<Candle>(aggregated.subList(aggregated.size() - MAX_BARS, aggregated.size()));
            }
            ChartResult result = new ChartResult();
            result.symbol = symbol;
            result.timeframe = timeframe;
            result.candles = aggregated;
            result.message = "Plotted " + aggregated.size() + " spot bars.";
            return result;
        }

        private List<Path> listFiles(String symbol) {
            Path dir = DATA_ROOT.resolve("daily_bin").resolve("spot").resolve(symbol);
            if (!Files.isDirectory(dir)) {
                return Collections.emptyList();
            }
            String prefix = symbol + "_spot_1m_";
            ArrayList<Path> files = new ArrayList<Path>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && name.startsWith(prefix) && name.endsWith(".bin")) {
                        files.add(p);
                    }
                }
            } catch (IOException ignored) {
            }
            Collections.sort(files, new Comparator<Path>() {
                @Override
                public int compare(Path a, Path b) {
                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                }
            });
            return files;
        }

        private void readChunk(Path path, long offset, int rows, List<Candle> out) throws IOException {
            long bytes = (long) rows * RECORD_SIZE;
            if (bytes > Integer.MAX_VALUE) {
                throw new IOException("Chunk too large.");
            }
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                MappedByteBuffer bb = ch.map(FileChannel.MapMode.READ_ONLY, offset, bytes);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < rows; i++) {
                    int base = i * RECORD_SIZE;
                    long closeTime = bb.getLong(base + 32);
                    if (!validMs(closeTime)) {
                        continue;
                    }
                    double open = bb.getDouble(base + 40);
                    double high = bb.getDouble(base + 48);
                    double low = bb.getDouble(base + 56);
                    double close = bb.getDouble(base + 64);
                    double volume = bb.getDouble(base + 72);
                    if (finite(open) && finite(high) && finite(low) && finite(close) && finite(volume)) {
                        out.add(new Candle(closeTime, open, high, low, close, volume));
                    }
                }
            }
        }

        private Candle readLatest(Path path) throws IOException {
            long size = Files.size(path);
            if (size < RECORD_SIZE || size % RECORD_SIZE != 0) {
                return null;
            }
            long total = size / RECORD_SIZE;
            int take = (int) Math.min(16L, total);
            ArrayList<Candle> out = new ArrayList<Candle>();
            readChunk(path, (total - take) * RECORD_SIZE, take, out);
            return out.isEmpty() ? null : out.get(out.size() - 1);
        }

        private ArrayList<Candle> dedupe(List<Candle> rows) {
            ArrayList<Candle> out = new ArrayList<Candle>();
            for (Candle c : rows) {
                if (!out.isEmpty() && out.get(out.size() - 1).timeMs == c.timeMs) {
                    out.set(out.size() - 1, c);
                } else {
                    out.add(c);
                }
            }
            return out;
        }

        private List<Candle> aggregate(List<Candle> rows, int frameMinutes) {
            if (frameMinutes <= 1 || rows.isEmpty()) {
                return rows;
            }
            long frameMs = frameMinutes * 60_000L;
            ArrayList<Candle> out = new ArrayList<Candle>();
            long bucket = -1L;
            double open = 0, high = 0, low = 0, close = 0, volume = 0;
            for (Candle c : rows) {
                long b = c.timeMs - (c.timeMs % frameMs);
                if (bucket != b) {
                    if (bucket >= 0) {
                        out.add(new Candle(bucket + frameMs - 1, open, high, low, close, volume));
                    }
                    bucket = b;
                    open = c.open;
                    high = c.high;
                    low = c.low;
                    close = c.close;
                    volume = c.volume;
                } else {
                    high = Math.max(high, c.high);
                    low = Math.min(low, c.low);
                    close = c.close;
                    volume += c.volume;
                }
            }
            if (bucket >= 0) {
                out.add(new Candle(bucket + frameMs - 1, open, high, low, close, volume));
            }
            return out;
        }

        private int frameMinutes(String tf) {
            if ("5m".equals(tf)) return 5;
            if ("15m".equals(tf)) return 15;
            if ("30m".equals(tf)) return 30;
            if ("1h".equals(tf)) return 60;
            if ("2h".equals(tf)) return 120;
            if ("4h".equals(tf)) return 240;
            if ("D".equals(tf)) return 1440;
            return 1;
        }
    }

    private static final class DataService {
        List<Map<String, Object>> tradeSummary() {
            if (!tableExists(ticketsDb(), "table_hyperliquid")) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> rows = sqliteRows(ticketsDb(), "SELECT symbol, trade_number, size, creation_mid_price FROM \"table_hyperliquid\"");
            LinkedHashMap<String, LinkedHashMap<String, Object>> agg = new LinkedHashMap<String, LinkedHashMap<String, Object>>();
            for (Map<String, Object> r : rows) {
                String sym = stringValue(r.get("symbol")).trim();
                if (sym.isEmpty()) {
                    continue;
                }
                LinkedHashMap<String, Object> dst = agg.get(sym);
                if (dst == null) {
                    dst = new LinkedHashMap<String, Object>();
                    dst.put("symbol", sym);
                    dst.put("target_qty", 0.0);
                    dst.put("ref_price", null);
                    dst.put("notional", null);
                    agg.put(sym, dst);
                }
                int tn = (int) safeDouble(r.get("trade_number"), 0.0);
                double size = safeDouble(r.get("size"), 0.0);
                double cmid = safeDouble(r.get("creation_mid_price"), 0.0);
                double target = safeDouble(dst.get("target_qty"), 0.0) + (tn >= 0 ? 1.0 : -1.0) * size;
                double ref = Math.max(safeDouble(dst.get("ref_price"), 0.0), cmid);
                dst.put("target_qty", target);
                dst.put("ref_price", ref > 0.0 ? ref : null);
                dst.put("notional", ref > 0.0 ? Math.abs(target) * ref : null);
            }
            return sortedRows(agg);
        }

        List<Map<String, Object>> openTrades() {
            if (!tableExists(ticketsDb(), "table_hyperliquid")) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> rows = sqliteRows(ticketsDb(), "SELECT * FROM \"table_hyperliquid\" ORDER BY symbol ASC, bot_id ASC, trade_number DESC");
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> r : rows) {
                double size = safeDouble(r.get("size"), 0.0);
                double filled = safeDouble(r.get("filled_qty"), 0.0);
                if (Math.abs(size) <= EPS && Math.abs(filled) <= EPS) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>(r);
                row.put("strategy", extractStrategy(row));
                row.put("side", detectSide(row));
                double px = safeDouble(firstPresent(row, new String[]{"open_activation_price", "creation_mid_price", "entry_price"}, 0.0), 0.0);
                row.put("notional_volume", px > 0.0 ? Math.abs(size) * px : null);
                out.add(row);
            }
            return out;
        }

        List<Map<String, Object>> marketRulesCombined() {
            if (!Files.exists(marketRulesDb())) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> tables = sqliteRows(marketRulesDb(), "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
            LinkedHashMap<String, LinkedHashMap<String, Object>> agg = new LinkedHashMap<String, LinkedHashMap<String, Object>>();
            for (Map<String, Object> t : tables) {
                String table = stringValue(t.get("name"));
                List<Map<String, Object>> rows = sqliteRows(marketRulesDb(), "SELECT * FROM " + quoteIdent(table));
                for (Map<String, Object> r : rows) {
                    String sym = stringValue(firstPresent(r, new String[]{"symbol", "coin"}, "")).trim();
                    if (sym.isEmpty()) {
                        continue;
                    }
                    LinkedHashMap<String, Object> dst = agg.get(sym);
                    if (dst == null) {
                        dst = new LinkedHashMap<String, Object>();
                        dst.put("symbol", sym);
                        agg.put(sym, dst);
                    }
                    for (Map.Entry<String, Object> e : r.entrySet()) {
                        String k = e.getKey();
                        if (!"symbol".equals(k) && !"coin".equals(k) && !dst.containsKey(k)) {
                            dst.put(k, e.getValue());
                        }
                    }
                }
            }
            return sortedRows(agg);
        }

        PnlResult pnl(String search) {
            List<Map<String, Object>> closed = historyRows();
            List<Map<String, Object>> open = openTrades();
            if (search != null && !search.isEmpty()) {
                closed = filterRows(closed, search);
                open = filterRows(open, search);
            }
            PnlResult r = new PnlResult();
            r.closedRows = closed;
            r.openRows = open;
            r.curve = buildEquityCurve(closed);
            r.stats = computeStats(closed, open, r.curve);
            return r;
        }

        private List<Map<String, Object>> historyRows() {
            String table = firstExistingTable(historyDb(), new String[]{"history_trades", "history_trade", "trades"});
            if (table == null) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> rows = sqliteRows(historyDb(), "SELECT * FROM " + quoteIdent(table));
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> r : rows) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>(r);
                row.put("symbol", extractSymbol(row));
                row.put("side", detectSide(row));
                row.put("strategy", extractStrategy(row));
                row.put("net_pnl", tradeNetPnl(row));
                row.put("gross_pnl", tradeNetPnl(row));
                row.put("commission_paid", Math.abs(safeDouble(firstPresent(row, new String[]{"trade fee", "commission", "commission_paid", "fees"}, 0.0), 0.0)));
                row.put("open_time_epoch", tradeOpenTs(row));
                row.put("close_time_epoch", tradeCloseTs(row));
                out.add(row);
            }
            return out;
        }

        private LinkedHashMap<String, Object> computeStats(List<Map<String, Object>> closed, List<Map<String, Object>> open, List<CurvePoint> curve) {
            double net = 0.0;
            double gp = 0.0;
            double gl = 0.0;
            int wins = 0;
            int losses = 0;
            for (Map<String, Object> r : closed) {
                double pnl = safeDouble(r.get("net_pnl"), 0.0);
                net += pnl;
                if (pnl > 0) {
                    wins++;
                    gp += pnl;
                } else if (pnl < 0) {
                    losses++;
                    gl += pnl;
                }
            }
            double dd = 0.0;
            for (CurvePoint p : curve) {
                dd = Math.min(dd, p.drawdown);
            }
            LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("Closed trades", closed.size());
            out.put("Open trades", open.size());
            out.put("Net P&L", net);
            out.put("Gross profit", gp);
            out.put("Gross loss", gl);
            out.put("Max drawdown", dd);
            out.put("Winning trades", wins);
            out.put("Losing trades", losses);
            out.put("Percent profitable", closed.isEmpty() ? null : (100.0 * wins / closed.size()));
            out.put("Avg P&L", closed.isEmpty() ? null : net / closed.size());
            return out;
        }

        private List<CurvePoint> buildEquityCurve(List<Map<String, Object>> rows) {
            ArrayList<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(rows);
            Collections.sort(sorted, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> a, Map<String, Object> b) {
                    return Double.compare(safeDouble(a.get("close_time_epoch"), 0.0), safeDouble(b.get("close_time_epoch"), 0.0));
                }
            });
            ArrayList<CurvePoint> out = new ArrayList<CurvePoint>();
            double equity = 0.0;
            double peak = 0.0;
            int idx = 0;
            for (Map<String, Object> r : sorted) {
                idx++;
                equity += safeDouble(r.get("net_pnl"), 0.0);
                peak = Math.max(peak, equity);
                out.add(new CurvePoint(idx, equity, equity - peak));
            }
            return out;
        }

        private List<Map<String, Object>> sqliteRows(Path db, String sql) {
            if (!Files.exists(db)) {
                return Collections.emptyList();
            }
            String encodedSql = Base64.getEncoder().encodeToString(sql.getBytes(StandardCharsets.UTF_8));
            String script = "import sqlite3,sys,base64\n"
                    + "db=sys.argv[1]; sql=base64.b64decode(sys.argv[2]).decode('utf-8')\n"
                    + "con=sqlite3.connect(db); con.row_factory=sqlite3.Row\n"
                    + "cur=con.execute(sql); cols=[d[0] for d in cur.description]\n"
                    + "def enc(x):\n"
                    + "    if x is None: return ''\n"
                    + "    return base64.b64encode(str(x).encode('utf-8')).decode('ascii')\n"
                    + "print('\\t'.join(enc(c) for c in cols))\n"
                    + "for r in cur.fetchall(): print('\\t'.join(enc(r[c]) for c in cols))\n";
            List<String> lines = runPython(script, db.toString(), encodedSql);
            if (lines.isEmpty()) {
                return Collections.emptyList();
            }
            String[] headerParts = lines.get(0).split("\t", -1);
            ArrayList<String> cols = new ArrayList<String>();
            for (String h : headerParts) {
                cols.add(decode64(h));
            }
            ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split("\t", -1);
                LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
                for (int c = 0; c < cols.size(); c++) {
                    row.put(cols.get(c), c < parts.length ? decode64(parts[c]) : "");
                }
                rows.add(row);
            }
            return rows;
        }

        private List<String> runPython(String script, String arg1, String arg2) {
            ArrayList<String> lines = new ArrayList<String>();
            String[][] commands = {{"python", "-c", script, arg1, arg2}, {"py", "-3", "-c", script, arg1, arg2}};
            for (String[] cmd : commands) {
                try {
                    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            lines.add(line);
                        }
                    }
                    if (p.waitFor() == 0) {
                        return lines;
                    }
                    lines.clear();
                } catch (Exception ignored) {
                    lines.clear();
                }
            }
            return Collections.emptyList();
        }

        private boolean tableExists(Path db, String table) {
            return !sqliteRows(db, "SELECT name FROM sqlite_master WHERE type='table' AND name=" + sqlString(table)).isEmpty();
        }

        private String firstExistingTable(Path db, String[] names) {
            for (String n : names) {
                if (tableExists(db, n)) {
                    return n;
                }
            }
            List<Map<String, Object>> rows = sqliteRows(db, "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
            return rows.isEmpty() ? null : stringValue(rows.get(0).get("name"));
        }
    }

    private static final class TableSection extends JPanel {
        private final SmartTableModel model = new SmartTableModel();
        private final JTable table = new JTable(model);
        private final TableRowSorter<SmartTableModel> sorter = new TableRowSorter<SmartTableModel>(model);
        private final JTextField search = new JTextField(20);
        private final JLabel count = new JLabel("0");

        TableSection(String title) {
            this(title, false);
        }

        TableSection(String title, boolean largeText) {
            super(new BorderLayout(6, 6));
            setBackground(BG);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xCBD5E1), 1), new EmptyBorder(12, 12, 12, 12)));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setRowSorter(sorter);
            styleTable(table);
            if (largeText) {
                table.setFont(table.getFont().deriveFont(Font.BOLD, 16f));
                table.setRowHeight(30);
                setPreferredSize(new Dimension(360, 300));
            } else {
                setPreferredSize(new Dimension(900, 250));
            }
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            top.setOpaque(false);
            JLabel t = label(title);
            t.setFont(t.getFont().deriveFont(Font.BOLD, largeText ? 20f : 15f));
            top.add(t);
            top.add(label("Search"));
            styleTextField(search);
            top.add(search);
            count.setForeground(MUTED);
            count.setFont(count.getFont().deriveFont(Font.BOLD, 12f));
            top.add(count);
            add(top, BorderLayout.NORTH);
            add(wrapTable(table), BorderLayout.CENTER);
            search.getDocument().addDocumentListener(new SimpleDocumentListener() {
                @Override
                void changed() {
                    applyFilter();
                }
            });
        }

        void setRows(List<Map<String, Object>> rows) {
            model.setRows(rows);
            count.setText(String.valueOf(rows.size()));
            applyFilter();
        }

        private void applyFilter() {
            final String q = search.getText().trim().toLowerCase(Locale.ROOT);
            sorter.setRowFilter(q.isEmpty() ? null : new javax.swing.RowFilter<SmartTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends SmartTableModel, ? extends Integer> entry) {
                    for (int i = 0; i < entry.getValueCount(); i++) {
                        if (displayValue(entry.getValue(i)).toLowerCase(Locale.ROOT).contains(q)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }

    private static final class SmartTableModel extends AbstractTableModel {
        private final List<String> cols = new ArrayList<String>();
        private final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();

        void setRows(List<Map<String, Object>> newRows) {
            cols.clear();
            rows.clear();
            LinkedHashSet<String> keys = new LinkedHashSet<String>();
            for (Map<String, Object> r : newRows) {
                keys.addAll(r.keySet());
            }
            cols.addAll(keys);
            rows.addAll(newRows);
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.size();
        }

        @Override
        public String getColumnName(int column) {
            return cols.get(column);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex).get(cols.get(columnIndex));
        }
    }

    private static final class SymbolStatusModel extends AbstractTableModel {
        private final String[] cols = new String[]{"Live", "Symbol", "Last Close", "Updated"};
        private final List<SymbolStatus> rows = new ArrayList<SymbolStatus>();
        private final LinkedHashMap<String, String> signatures = new LinkedHashMap<String, String>();

        void setSymbols(List<String> symbols) {
            LinkedHashMap<String, SymbolStatus> existing = new LinkedHashMap<String, SymbolStatus>();
            for (SymbolStatus s : rows) {
                existing.put(s.symbol, s);
            }
            rows.clear();
            for (String symbol : symbols) {
                SymbolStatus old = existing.get(symbol);
                rows.add(old == null ? new SymbolStatus(symbol, false, null, 0.0, "pending", "") : old);
            }
            fireTableDataChanged();
        }

        void setRows(List<SymbolStatus> newRows) {
            rows.clear();
            for (SymbolStatus s : newRows) {
                String old = signatures.get(s.symbol);
                s.live = s.live || (old != null && !old.equals(s.signature));
                signatures.put(s.symbol, s.signature);
                rows.add(s);
            }
            fireTableDataChanged();
        }

        void updateStatus(SymbolStatus status) {
            String old = signatures.get(status.symbol);
            status.live = status.live || (old != null && !old.equals(status.signature));
            signatures.put(status.symbol, status.signature);
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).symbol.equals(status.symbol)) {
                    rows.set(i, status);
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
            rows.add(status);
            int row = rows.size() - 1;
            fireTableRowsInserted(row, row);
        }

        boolean contains(String symbol) {
            return find(symbol) != null;
        }

        SymbolStatus find(String symbol) {
            for (SymbolStatus s : rows) {
                if (s.symbol.equals(symbol)) {
                    return s;
                }
            }
            return null;
        }

        String symbolAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row).symbol : "";
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SymbolStatus s = rows.get(rowIndex);
            if (columnIndex == 0) return "\u25CF";
            if (columnIndex == 1) return s.symbol;
            if (columnIndex == 2) return s.lastTimeMs == null ? "-" : formatPlainNumber(s.lastClose);
            return s.age;
        }

        boolean isLive(int row) {
            return row >= 0 && row < rows.size() && rows.get(row).live;
        }
    }

    private static final class SymbolStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            boolean live = ((SymbolStatusModel) table.getModel()).isLive(modelRow);
            if (!selected) {
                l.setBackground(row % 2 == 0 ? BG : STRIPE);
                l.setForeground(column == 0 ? (live ? GREEN : RED) : INK);
            } else if (column == 0) {
                l.setForeground(live ? GREEN : RED);
            }
            l.setText(displayValue(value));
            l.setBorder(new EmptyBorder(0, 7, 0, 7));
            if (column == 0) {
                l.setHorizontalAlignment(JLabel.CENTER);
                l.setFont(table.getFont().deriveFont(Font.BOLD, table.getFont().getSize2D() + 4.0f));
            } else {
                l.setHorizontalAlignment(column == 2 ? JLabel.RIGHT : JLabel.LEFT);
            }
            return l;
        }
    }

    private static final class SymbolActiveModel extends AbstractTableModel {
        private final List<String> symbols = new ArrayList<String>();
        private final List<Boolean> disabled = new ArrayList<Boolean>();

        void setRows(Map<String, Boolean> rows) {
            symbols.clear();
            disabled.clear();
            ArrayList<String> keys = new ArrayList<String>(rows.keySet());
            Collections.sort(keys);
            for (String k : keys) {
                symbols.add(k);
                disabled.add(rows.get(k));
            }
            fireTableDataChanged();
        }

        void put(String symbol, Boolean value) {
            int idx = symbols.indexOf(symbol);
            if (idx >= 0) {
                disabled.set(idx, value);
            } else {
                symbols.add(symbol);
                disabled.add(value);
            }
            fireTableDataChanged();
        }

        Map<String, Boolean> asMap() {
            LinkedHashMap<String, Boolean> out = new LinkedHashMap<String, Boolean>();
            for (int i = 0; i < symbols.size(); i++) {
                out.put(symbols.get(i), disabled.get(i));
            }
            return out;
        }

        @Override
        public int getRowCount() {
            return symbols.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "symbol" : "disabled";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return columnIndex == 0 ? symbols.get(rowIndex) : disabled.get(rowIndex);
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                symbols.set(rowIndex, stringValue(aValue));
            } else {
                disabled.set(rowIndex, Boolean.TRUE.equals(aValue));
            }
        }
    }

    private static final class SymbolActive {
        boolean allDisabled;
        final LinkedHashMap<String, Boolean> symbols = new LinkedHashMap<String, Boolean>();

        static SymbolActive read(Path path) {
            SymbolActive active = new SymbolActive();
            if (!Files.exists(path)) {
                return active;
            }
            try {
                String s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                active.allDisabled = s.matches("(?s).*\"all_symbols_disabled\"\\s*:\\s*true.*");
                Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)").matcher(section(s, "symbols"));
                while (m.find()) {
                    active.symbols.put(unescapeJson(m.group(1)), Boolean.valueOf(m.group(2)));
                }
            } catch (IOException ignored) {
            }
            return active;
        }

        void write(Path path) throws IOException {
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                w.write("{\n  \"all_symbols_disabled\": " + allDisabled + ",\n  \"symbols\": {\n");
                int i = 0;
                for (Map.Entry<String, Boolean> e : symbols.entrySet()) {
                    w.write("    \"" + escapeJson(e.getKey()) + "\": " + e.getValue());
                    i++;
                    w.write(i < symbols.size() ? ",\n" : "\n");
                }
                w.write("  }\n}\n");
            }
        }
    }

    private static final class ChartCanvas extends JPanel {
        private ChartResult result = ChartResult.message("Load a symbol.");

        ChartCanvas() {
            setBackground(BG);
            setPreferredSize(new Dimension(1050, 620));
        }

        void setResult(ChartResult result) {
            this.result = result;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (result.candles.isEmpty()) {
                drawCentered(g2, result.message);
                g2.dispose();
                return;
            }

            int left = 82;
            int right = 96;
            int top = 50;
            int bottom = 58;
            int gap = 10;
            int volH = Math.max(92, getHeight() / 6);
            int priceH = getHeight() - top - bottom - gap - volH;
            int volY = top + priceH + gap;
            int w = getWidth() - left - right;

            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double maxVol = 0;
            for (Candle c : result.candles) {
                min = Math.min(min, c.low);
                max = Math.max(max, c.high);
                maxVol = Math.max(maxVol, c.volume);
            }
            if (max <= min) {
                max = min + 1.0;
            }
            if (maxVol <= 0) {
                maxVol = 1.0;
            }

            drawGrid(g2, left, top, w, priceH, 5);
            drawGrid(g2, left, volY, w, volH, 2);
            g2.setColor(INK);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 17f));
            g2.drawString(result.symbol + " Spot OHLCV", left, 24);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            g2.setColor(MUTED);
            g2.drawString(result.timeframe + " timeframe - latest " + result.candles.size() + " bars", left, 42);
            g2.setColor(INK);
            g2.drawString("Price", left + 8, top + 18);
            g2.drawString("Volume", left + 8, volY + 18);
            drawRotated(g2, "Price", 22, top + priceH / 2 + 20);
            drawRotated(g2, "Volume", 22, volY + volH / 2 + 24);
            String xLabel = "Time (UTC)";
            g2.drawString(xLabel, left + (w - g2.getFontMetrics().stringWidth(xLabel)) / 2, getHeight() - 10);

            int n = result.candles.size();
            double step = n <= 1 ? w : w / (double) (n - 1);
            int bodyW = Math.max(2, Math.min(10, (int) (step * 0.65)));
            for (int i = 0; i < n; i++) {
                Candle c = result.candles.get(i);
                int x = left + (int) Math.round(i * step);
                int highY = scale(c.high, min, max, top, priceH);
                int lowY = scale(c.low, min, max, top, priceH);
                int openY = scale(c.open, min, max, top, priceH);
                int closeY = scale(c.close, min, max, top, priceH);
                g2.setColor(c.close >= c.open ? GREEN : RED);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(x, highY, x, lowY);
                g2.fillRect(x - bodyW / 2, Math.min(openY, closeY), bodyW, Math.max(1, Math.abs(closeY - openY)));
                int vy = volY + volH - (int) Math.round((c.volume / maxVol) * volH);
                g2.drawLine(x, volY + volH, x, vy);
            }

            g2.setColor(INK);
            drawScale(g2, left + w + 8, top, priceH, min, max);
            drawScale(g2, left + w + 8, volY, volH, 0.0, maxVol);
            Candle first = result.candles.get(0);
            Candle last = result.candles.get(result.candles.size() - 1);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
            for (int i = 0; i <= 4; i++) {
                int idx = Math.min(result.candles.size() - 1, Math.round(i * (result.candles.size() - 1) / 4.0f));
                Candle tick = result.candles.get(idx);
                int x = left + (int) Math.round(idx * step);
                g2.setColor(GRID);
                g2.drawLine(x, volY + volH, x, volY + volH + 5);
                g2.setColor(INK);
                String s = formatAxisTime(tick.timeMs);
                int sw = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, Math.max(left, Math.min(left + w - sw, x - sw / 2)), volY + volH + 21);
            }
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
            g2.drawString("Start " + formatAxisTime(first.timeMs), left, getHeight() - 28);
            String end = "End " + formatAxisTime(last.timeMs);
            g2.drawString(end, Math.max(left, left + w - g2.getFontMetrics().stringWidth(end)), getHeight() - 28);
            g2.dispose();
        }

        private void drawGrid(Graphics2D g2, int x, int y, int w, int h, int rows) {
            g2.setColor(GRID);
            g2.drawRect(x, y, w, h);
            for (int i = 1; i < rows; i++) {
                int yy = y + h * i / rows;
                g2.drawLine(x, yy, x + w, yy);
            }
            for (int i = 1; i < 8; i++) {
                int xx = x + w * i / 8;
                g2.drawLine(xx, y, xx, y + h);
            }
        }

        private void drawScale(Graphics2D g2, int x, int y, int h, double min, double max) {
            for (int i = 0; i <= 5; i++) {
                double v = max - (max - min) * i / 5.0;
                g2.drawString(formatNumber(v), x, y + h * i / 5 + 4);
            }
        }

        private int scale(double v, double min, double max, int y, int h) {
            return y + h - (int) Math.round(((v - min) / (max - min)) * h);
        }
    }

    private static final class EquityCanvas extends JPanel {
        private List<CurvePoint> points = Collections.emptyList();

        EquityCanvas() {
            setBackground(BG);
            setBorder(BorderFactory.createLineBorder(GRID));
            setPreferredSize(new Dimension(600, 260));
        }

        void setPoints(List<CurvePoint> points) {
            this.points = points;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (points == null || points.size() < 2) {
                drawCentered(g2, "No equity curve data.");
                g2.dispose();
                return;
            }
            int left = 78;
            int right = 82;
            int top = 48;
            int bottom = 58;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (CurvePoint p : points) {
                min = Math.min(min, p.equity);
                max = Math.max(max, p.equity);
            }
            if (Math.abs(max - min) < 1e-12) {
                max = min + 1.0;
            }
            g2.setColor(INK);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 17f));
            g2.drawString("Equity Curve", left, 24);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            g2.setColor(MUTED);
            g2.drawString(points.size() + " closed-trade points", left, 42);
            g2.setColor(INK);
            drawRotated(g2, "Net PnL / Equity", 22, top + h / 2 + 38);
            String xLabel = "Closed Trade Number";
            g2.drawString(xLabel, left + (w - g2.getFontMetrics().stringWidth(xLabel)) / 2, getHeight() - 12);
            g2.setColor(GRID);
            g2.drawRect(left, top, w, h);
            for (int i = 1; i < 5; i++) {
                int yy = top + h * i / 5;
                g2.drawLine(left, yy, left + w, yy);
            }
            g2.setColor(INK);
            drawScale(g2, left + w + 8, top, h, min, max);
            Path2D path = new Path2D.Double();
            for (int i = 0; i < points.size(); i++) {
                CurvePoint p = points.get(i);
                double x = left + (i / (double) (points.size() - 1)) * w;
                double y = top + h - ((p.equity - min) / (max - min)) * h;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            g2.setColor(BLUE);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(path);
            g2.setColor(INK);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
            g2.drawString("1", left, top + h + 19);
            String last = String.valueOf(points.size());
            g2.drawString(last, left + w - g2.getFontMetrics().stringWidth(last), top + h + 19);
            g2.dispose();
        }

        private void drawScale(Graphics2D g2, int x, int y, int h, double min, double max) {
            for (int i = 0; i <= 5; i++) {
                double v = max - (max - min) * i / 5.0;
                g2.drawString(formatNumber(v), x, y + h * i / 5 + 4);
            }
        }
    }

    private static final class Candle {
        final long timeMs;
        final double open, high, low, close, volume;

        Candle(long timeMs, double open, double high, double low, double close, double volume) {
            this.timeMs = timeMs;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    private static final class SymbolStatus {
        final String symbol;
        boolean live;
        final Long lastTimeMs;
        final double lastClose;
        final String age;
        final String signature;

        SymbolStatus(String symbol, boolean live, Long lastTimeMs, double lastClose, String age, String signature) {
            this.symbol = symbol;
            this.live = live;
            this.lastTimeMs = lastTimeMs;
            this.lastClose = lastClose;
            this.age = age;
            this.signature = signature;
        }
    }

    private static final class ChartResult {
        String symbol = "";
        String timeframe = "1m";
        String message = "";
        List<Candle> candles = Collections.emptyList();

        static ChartResult message(String message) {
            ChartResult result = new ChartResult();
            result.message = message;
            return result;
        }
    }

    private static final class PnlResult {
        List<Map<String, Object>> closedRows = Collections.emptyList();
        List<Map<String, Object>> openRows = Collections.emptyList();
        List<CurvePoint> curve = Collections.emptyList();
        LinkedHashMap<String, Object> stats = new LinkedHashMap<String, Object>();
    }

    private static final class CurvePoint {
        final int index;
        final double equity;
        final double drawdown;

        CurvePoint(int index, double equity, double drawdown) {
            this.index = index;
            this.equity = equity;
            this.drawdown = drawdown;
        }
    }

    private abstract static class SimpleDocumentListener implements DocumentListener {
        abstract void changed();

        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }
    }

    private static JPanel toolbar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GRID), new EmptyBorder(6, 6, 8, 6)));
        return p;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG);
        b.setForeground(INK);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT, 1), new EmptyBorder(7, 14, 7, 14)));
        return b;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(INK);
        return l;
    }

    private static void styleCombo(JComboBox<String> combo) {
        combo.setBackground(BG);
        combo.setForeground(INK);
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(BG);
        spinner.setForeground(INK);
    }

    private static void styleTextField(JTextField field) {
        field.setBackground(BG);
        field.setForeground(INK);
        field.setCaretColor(INK);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x94A3B8)), new EmptyBorder(6, 8, 6, 8)));
    }

    private static void styleTable(JTable table) {
        table.setBackground(BG);
        table.setForeground(INK);
        table.setGridColor(GRID);
        table.setSelectionBackground(new Color(0xDBEAFE));
        table.setSelectionForeground(INK);
        table.setRowHeight(30);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setBackground(HEADER);
        table.getTableHeader().setForeground(INK);
        table.getTableHeader().setReorderingAllowed(true);
        table.getTableHeader().setResizingAllowed(true);
        table.getTableHeader().setPreferredSize(new Dimension(20, 34));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 13f));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                l.setText(value == null ? "" : value.toString());
                l.setBackground(HEADER);
                l.setForeground(INK);
                l.setOpaque(true);
                l.setFont(t.getFont().deriveFont(Font.BOLD));
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, ACCENT), new EmptyBorder(0, 7, 0, 7)));
                return l;
            }
        });
        table.setShowGrid(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setText(displayValue(value));
                if (!selected) {
                    c.setBackground(row % 2 == 0 ? BG : STRIPE);
                    c.setForeground(INK);
                }
                setBorder(new EmptyBorder(0, 7, 0, 7));
                return c;
            }
        });
    }

    private static JScrollPane wrapTable(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xCBD5E1), 1));
        sp.getViewport().setBackground(BG);
        return sp;
    }

    private static JSplitPane splitVertical(java.awt.Component top, java.awt.Component bottom, double weight) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        styleSplit(split, weight);
        return split;
    }

    private static JSplitPane splitHorizontal(java.awt.Component left, java.awt.Component right, double weight) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        styleSplit(split, weight);
        return split;
    }

    private static void styleSplit(JSplitPane split, double weight) {
        split.setResizeWeight(weight);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setDividerSize(12);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(BG);
    }

    private static void applyUiScale(java.awt.Component c, float scale) {
        Font f = c.getFont();
        if (f != null && c instanceof javax.swing.JComponent) {
            javax.swing.JComponent jc = (javax.swing.JComponent) c;
            Font base = (Font) jc.getClientProperty("baseFont");
            if (base == null) {
                base = f;
                jc.putClientProperty("baseFont", base);
            }
            c.setFont(base.deriveFont(Math.max(9.0f, base.getSize2D() * scale)));
        }
        if (c instanceof JTable) {
            JTable table = (JTable) c;
            table.setRowHeight(Math.max(20, Math.round(30 * scale)));
            table.getTableHeader().setPreferredSize(new Dimension(20, Math.max(24, Math.round(34 * scale))));
        } else if (c instanceof JSplitPane) {
            ((JSplitPane) c).setDividerSize(Math.max(8, Math.round(12 * scale)));
        }
        if (c instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                applyUiScale(child, scale);
            }
        }
    }

    private static Path historyDb() {
        return WORKSPACE_ROOT.resolve("history_trade.sqlite");
    }

    private static Path ticketsDb() {
        return WORKSPACE_ROOT.resolve("tickets_hyperliquid_high_risk.sqlite");
    }

    private static Path marketRulesDb() {
        return WORKSPACE_ROOT.resolve("market_rules.sqlite");
    }

    private static Path symbolActivePath() {
        return WORKSPACE_ROOT.resolve("symbol_active.json");
    }

    private static List<Map<String, Object>> statsRows(Map<String, Object> stats) {
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Object> e : stats.entrySet()) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("metric", e.getKey());
            row.put("value", e.getValue());
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, String q) {
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : rows) {
            StringBuilder b = new StringBuilder();
            for (Object v : r.values()) {
                b.append(' ').append(displayValue(v));
            }
            if (b.toString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(r);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> sortedRows(LinkedHashMap<String, LinkedHashMap<String, Object>> map) {
        ArrayList<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String k : keys) {
            out.add(map.get(k));
        }
        return out;
    }

    private static Object firstPresent(Map<String, Object> row, String[] keys, Object def) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null && !stringValue(v).isEmpty()) {
                return v;
            }
        }
        return def;
    }

    private static String extractSymbol(Map<String, Object> row) {
        return stringValue(firstPresent(row, new String[]{"symbol", "coin", "asset"}, "")).trim();
    }

    private static String extractStrategy(Map<String, Object> row) {
        String s = stringValue(firstPresent(row, new String[]{"strategy", "tradeID", "trade_id_str", "trade_uid", "trade_id"}, "")).trim();
        for (String p : s.replace('-', '_').split("_")) {
            if (p.toLowerCase(Locale.ROOT).startsWith("s")) {
                return p;
            }
        }
        return "-";
    }

    private static String detectSide(Map<String, Object> row) {
        String side = stringValue(firstPresent(row, new String[]{"side", "direction"}, "")).trim().toLowerCase(Locale.ROOT);
        if ("long".equals(side) || "short".equals(side)) {
            return side;
        }
        int tn = (int) safeDouble(firstPresent(row, new String[]{"trade_number", "tradeNumber"}, 0.0), 0.0);
        if (tn > 0) return "long";
        if (tn < 0) return "short";
        String id = stringValue(firstPresent(row, new String[]{"tradeID", "trade_id_str", "trade_uid", "trade_id"}, "")).toLowerCase(Locale.ROOT);
        if (id.contains("_short")) return "short";
        if (id.contains("_long")) return "long";
        return "-";
    }

    private static Double tradeOpenTs(Map<String, Object> row) {
        return firstParsedTime(row, new String[]{"ex open time", "open_time", "creation time", "creation_time_datetime", "created_at"});
    }

    private static Double tradeCloseTs(Map<String, Object> row) {
        return firstParsedTime(row, new String[]{"ex close time", "close_time", "close activation time", "closed_at", "updated_at", "creation time"});
    }

    private static Double firstParsedTime(Map<String, Object> row, String[] keys) {
        for (String k : keys) {
            Double d = parseTime(row.get(k));
            if (d != null) return d;
        }
        return null;
    }

    private static Double parseTime(Object x) {
        if (x == null) return null;
        String s = x.toString().trim();
        if (s.isEmpty()) return null;
        Double n = nullableDouble(s);
        if (n != null) return n > 10_000_000_000L ? n / 1000.0 : n;
        String normalized = s.endsWith("Z") ? s.substring(0, s.length() - 1) + "+00:00" : s;
        normalized = normalized.replace(' ', 'T');
        try {
            return (double) OffsetDateTime.parse(normalized).toEpochSecond();
        } catch (Exception ignored) {
        }
        try {
            return (double) LocalDateTime.parse(normalized).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static double tradeNetPnl(Map<String, Object> row) {
        Double direct = nullableDouble(firstPresent(row, new String[]{"net_pnl", "net pnl", "pnl", "profit_loss", "profit"}, null));
        if (direct != null) return direct;
        String side = detectSide(row);
        double qty = Math.abs(safeDouble(firstPresent(row, new String[]{"volume symbol filled", "filled_qty", "volume symbol", "size", "qty"}, 0.0), 0.0));
        double entry = safeDouble(firstPresent(row, new String[]{"vwap ex price + fee", "entry_price", "vwap executed price", "open_activation_price", "creation price", "creation_mid_price"}, 0.0), 0.0);
        double exit = safeDouble(firstPresent(row, new String[]{"vwap ex close price", "exit_price", "close_price", "close activation price"}, 0.0), 0.0);
        if (qty <= EPS || entry <= 0.0 || exit <= 0.0) return 0.0;
        return "short".equals(side) ? (entry - exit) * qty : (exit - entry) * qty;
    }

    private static boolean validMs(long ms) {
        return ms >= MIN_VALID_MS && ms <= System.currentTimeMillis() + FUTURE_TOLERANCE_MS;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static Double nullableDouble(Object x) {
        if (x == null) return null;
        try {
            String s = x.toString().trim();
            if (s.isEmpty() || "-".equals(s)) return null;
            return Double.valueOf(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private static double safeDouble(Object x, double def) {
        Double d = nullableDouble(x);
        return d == null ? def : d;
    }

    private static String stringValue(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String displayValue(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number) {
            return formatPlainNumber(((Number) v).doubleValue());
        }
        String s = String.valueOf(v).trim();
        if (s.matches("[-+]?((\\d+\\.?\\d*)|(\\.\\d+))[eE][-+]?\\d+")) {
            try {
                return plainBigDecimal(new BigDecimal(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(v);
    }

    private static String formatPlainNumber(double v) {
        if (!finite(v)) {
            return "-";
        }
        return plainBigDecimal(BigDecimal.valueOf(v));
    }

    private static String plainBigDecimal(BigDecimal value) {
        BigDecimal plain = value.stripTrailingZeros();
        if (plain.signum() == 0) {
            return "0";
        }
        return plain.toPlainString();
    }

    private static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static String sqlString(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static String decode64(String s) {
        return s == null || s.isEmpty() ? "" : new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static void drawCentered(Graphics2D g2, String text) {
        g2.setColor(INK);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        int w = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, Math.max(10, (g2.getClipBounds().width - w) / 2), Math.max(30, g2.getClipBounds().height / 2));
    }

    private static void drawRotated(Graphics2D g2, String text, int x, int y) {
        AffineTransform old = g2.getTransform();
        g2.rotate(-Math.PI / 2.0, x, y);
        g2.drawString(text, x, y);
        g2.setTransform(old);
    }

    private static String formatNumber(double v) {
        if (!finite(v)) return "-";
        DecimalFormat df = Math.abs(v) >= 1000 ? new DecimalFormat("#,##0.##") : new DecimalFormat("0.######");
        return df.format(v);
    }

    private static String formatTime(long ms) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));
    }

    private static String formatAxisTime(long ms) {
        String s = formatTime(ms).replace('T', ' ');
        return s.length() > 16 ? s.substring(5, 16) : s;
    }

    private static String ageText(long ageMs) {
        if (ageMs < 0L) {
            return "future";
        }
        long seconds = ageMs / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + "h";
        }
        return (hours / 24L) + "d";
    }

    private static Path detectAppDir() {
        try {
            Path p = Paths.get(FundModelSwingClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(p)) {
                return p.getParent();
            }
            if (Files.isDirectory(p)) {
                return p;
            }
        } catch (Exception ignored) {
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private static String cleanError(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static String section(String json, String name) {
        int key = json.indexOf("\"" + name + "\"");
        if (key < 0) return "";
        int start = json.indexOf('{', key);
        if (start < 0) return "";
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return "";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) {
                out.append(ch);
                esc = false;
            } else if (ch == '\\') {
                esc = true;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
