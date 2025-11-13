package Demo1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.*;
import java.util.List;

/*
 * 缓存一致性监听协议(Snoop)仿真系统
 * 模拟多个CPU节点的缓存状态变化和监听协议工作过程
 */
public class Snoop extends JFrame {
    // 系统配置
    private static final int NUM_CPU_NODES = 4;	// CPU节点数量
    private static final int CACHE_BLOCKS = 16;	// 每个CPU的缓存块数量
    private static final int BLOCK_SIZE = 16;	// 每个缓存块的数据大小(字节)

    // 状态枚举
    enum CacheState { INVALID("I"), SHARED("S"), MODIFIED("M");
        private final String abbr;
        CacheState(String abbr) { this.abbr = abbr; }
        public String getAbbr() { return abbr; }
    }

    // CPU节点数据结构
    static class CPUNode {
        String id;					// CPU节点标识符
        CacheBlock[] cacheBlocks;	// 缓存块数组

        public CPUNode(String id) {
            this.id = id;
            this.cacheBlocks = new CacheBlock[CACHE_BLOCKS];
            Arrays.setAll(cacheBlocks, i -> new CacheBlock());
        }
    }

    // Cache块数据结构
    static class CacheBlock {
    	String index;
    	String offset;
        String tag;			 // 缓存块标记(存储内存地址的高位部分)
        CacheState state;	 // 缓存块状态(INVALID/SHARED/MODIFIED)
        String[] data;		 // 缓存块数据(16字节)
        // MODIFIED状态时为true，写回主存后重置为false
        boolean isDirty = false; 

        public CacheBlock() {
            this.tag = "-";
            this.state = CacheState.INVALID;
            this.data = new String[BLOCK_SIZE];
            Arrays.fill(data, "0"); // 初始数据为0
        }
    }

    // 主界面组件
    private JPanel cpuNodesPanel;		// CPU节点显示面板
    private JTextArea detailArea;       // 请求详情显示区域
    private JList<String> historyList;  // 请求历史列表
    private DefaultListModel<String> historyModel;  // 历史列表数据模型
    private JComboBox<String> cpuCombo;	 // CPU节点选择下拉框

    // 系统状态
    private CPUNode[] cpuNodes;			 // CPU节点数组
    // 请求历史记录
    private List<Map<String, Object>> requestHistory = new ArrayList<>();

    /*
     * 构造函数，初始化仿真系统
     */
    public Snoop() {
        initUI();		// 初始化用户界面
        initMainMemory();//初始化主存
        initSystem();	// 初始化系统状态
        renderUI();		// 渲染用户界面
    }

    private void initUI() {
        setTitle("Snoop仿真系统");
        setSize(2000, 1050);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));
    }

    private void initSystem() {
        cpuNodes = new CPUNode[NUM_CPU_NODES];
        Arrays.setAll(cpuNodes, i -> new CPUNode("CPU" + String.format("%02d", Integer.parseInt(Integer.toBinaryString(i)))));
    }

    private void renderUI() {
        // 顶部控制面板
        JPanel controlPanel = createControlPanel();
        
        // 中部CPU节点显示区
        cpuNodesPanel = createCPUNodesPanel();

        // 底部历史记录和详情面板
        JSplitPane bottomPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> showRequestDetails());
        
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setBackground(UIManager.getColor("Panel.background"));
        
        bottomPane.setTopComponent(new JScrollPane(historyList));
        bottomPane.setBottomComponent(new JScrollPane(detailArea));
        bottomPane.setDividerLocation(0.3);

        // 整体布局
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(cpuNodesPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPane, BorderLayout.SOUTH);
        
        add(mainPanel);
    }

    /*
     * 创建控制面板，包含地址输入、操作类型选择等组件
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new TitledBorder("内存请求"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 地址输入
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("内存地址 (24位十六进制):"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        JTextField addressField = new JTextField("0x000000", 12);
        panel.add(addressField, gbc);

        // 操作类型
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("操作类型:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        JComboBox<String> operationCombo = new JComboBox<>(new String[]{"读", "写"});
        panel.add(operationCombo, gbc);

        // 写入值
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("写入值 (16字节十六进制):"), gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        JTextField writeValueField = new JTextField("0000000000000000", 20);
        panel.add(writeValueField, gbc);

        // CPU节点选择
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("目标CPU节点:"), gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        String[] cpuIds = Arrays.stream(cpuNodes).map(n -> n.id).toArray(String[]::new);
        cpuCombo = new JComboBox<>(cpuIds);
        panel.add(cpuCombo, gbc);

        // 按钮
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton executeBtn = new JButton("执行请求");
        JButton resetBtn = new JButton("重置系统");
        
        executeBtn.addActionListener(e -> processRequest(addressField, operationCombo, writeValueField));
        resetBtn.addActionListener(e -> resetSystem());
        
        buttonPanel.add(executeBtn);
        buttonPanel.add(resetBtn);
        panel.add(buttonPanel, gbc);

        return panel;
    }

    /*
     * 创建CPU节点显示面板
     */
    private JPanel createCPUNodesPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new TitledBorder("CPU Cache状态"));
        
        // 为每个CPU节点创建一个面板
        for (CPUNode node : cpuNodes) {
            JPanel cpuPanel = new JPanel(new BorderLayout());
            cpuPanel.setBorder(new TitledBorder(node.id));
            
            JTable table = new JTable(createCacheTableModel(node));
            table.setRowHeight(25);
            table.getColumnModel().getColumn(0).setPreferredWidth(50);
            table.getColumnModel().getColumn(1).setPreferredWidth(60);
            table.getColumnModel().getColumn(2).setPreferredWidth(40);
            table.getColumnModel().getColumn(3).setPreferredWidth(120);
            
            cpuPanel.add(new JScrollPane(table), BorderLayout.CENTER);
            panel.add(cpuPanel);
        }
        
        return panel;
    }

    /*
     * 创建缓存表模型，用于在表格中显示缓存状态
     */
    private DefaultTableModel createCacheTableModel(CPUNode node) {
        String[] columns = {"索引", "标记", "状态", "数据 (16字节)"};
        Object[][] data = new Object[CACHE_BLOCKS][4];
        
        for (int i = 0; i < CACHE_BLOCKS; i++) {
            CacheBlock block = node.cacheBlocks[i];
            data[i][0] = i;
            data[i][1] = block.tag;
            data[i][2] = block.state.getAbbr(); // 显示状态缩写
            data[i][3] = String.join("", block.data);
        }
        
        return new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private Map<String, String[]> mainMemory = new HashMap<>(); // 主存：地址→数据（16字节）
    // 初始化主存
    private void initMainMemory() {
        // 模拟主存包含所有可能的地址（简化处理，实际可按需生成）
        for (int i = 0; i < 0x100000; i++) { // 假设主存地址范围0x000000~0xFFFFF
            String address = String.format("0x%06X", i);
            mainMemory.put(address, new String[BLOCK_SIZE]);
            Arrays.fill(mainMemory.get(address), "0"); // 初始数据全为0
        }
    }
    
    /*
     * 处理内存请求
     */
    private void processRequest(JTextField addressField, JComboBox<String> operationCombo, JTextField writeValueField) {
        String address = addressField.getText().toUpperCase();
        String operation = (String) operationCombo.getSelectedItem();
        String writeValue = writeValueField.getText().toUpperCase();
        String targetCpuId = (String) cpuCombo.getSelectedItem();
        CPUNode targetNode = getTargetNode(targetCpuId);

        if (!validateInput(address, operation, writeValue)) return;

         // 解析地址，提取标签和索引
        String tag = address.substring(2, 6);  
        String index = address.substring(6, 7);  
        int cacheIndex = Integer.parseInt(index, 16); 

        // 实际判断缓存命中/缺失
        boolean cacheHit = checkCacheHit(targetNode, tag, cacheIndex);
        
        recordRequest(address, operation, targetCpuId, cacheHit);
        updateCache(targetNode, operation, writeValue, address, cacheHit);
        renderCPUNodes();
    }

    // 检查缓存是否命中
    private boolean checkCacheHit(CPUNode node, String tag, int cacheIndex) {
        CacheBlock block = node.cacheBlocks[cacheIndex];
        // 标签匹配且状态有效（非INVALID）
        return block.tag.equals(tag) && block.state != CacheState.INVALID;
    }
    
    /*
     * 验证用户输入的有效性
     */
    private boolean validateInput(String address, String operation, String writeValue) {
    	// 验证地址格式(24位十六进制)
    	if (!address.matches("^0X[0-9A-F]{6}$")) {
            JOptionPane.showMessageDialog(this, "无效地址格式（需为24位十六进制）");
            return false;
        }
    	// 验证写入值格式(16字节十六进制)
        if (operation.equals("写") && !writeValue.matches("^[0-9A-F]{16}$")) {
            JOptionPane.showMessageDialog(this, "写入值必须为16字节十六进制");
            return false;
        }
        return true;
    }

    /*
     * 记录内存请求历史
     */
    private void recordRequest(String address, String operation, String targetCpuId, boolean cacheHit) {
        Map<String, Object> request = new HashMap<>();
        request.put("address", address);
        request.put("operation", operation);
        request.put("targetCpu", targetCpuId);
        request.put("cacheHit", cacheHit);
        request.put("details", getOperationDetails(operation, cacheHit));

        // 添加到历史记录的开头(最新记录显示在最前)
        requestHistory.add(0, request);
        // 保持最多10条记录
        if (requestHistory.size() > 10) requestHistory.remove(10);
        updateHistoryList();
    }

    /*
     * 获取操作详情描述
     */
    private List<String> getOperationDetails(String operation, boolean cacheHit) {
        return Arrays.asList(
            String.format("操作类型: %s", operation),
            String.format("Cache状态: %s", cacheHit ? "命中" : "缺失"),
            operation.equals("写") ? "更新缓存状态为MODIFIED" : "读取缓存数据"
        );
    }

    /*
     * 更新缓存状态
     */
    private void updateCache(CPUNode targetNode, String operation, String writeValue, String address, boolean cacheHit) {
    	// 缓存缺失时，加载数据到缓存
    	String hexAddress = address.toUpperCase().substring(2);
    	char indexChar = hexAddress.charAt(4);
    	int cacheIndex = Character.digit(indexChar, 16); 
    	CacheBlock block = targetNode.cacheBlocks[cacheIndex];
    	block.index = address.substring(6, 7);
        block.tag = address.substring(2, 6); 
        block.offset= address.substring(7, 8);
        
    	if (!cacheHit) {
    		if (block.state == CacheState.MODIFIED && block.isDirty) {
                writeBackToMainMemory(block); // 写回主存
            }
    		loadBlock(address, block, targetNode);
        }
    	 // 写操作时更新缓存数据和状态
        if (operation.equals("写")) {
            block.state = CacheState.MODIFIED;
            block.isDirty = true; // 写回法标记
            block.data = writeValue.toUpperCase().split(""); 
            invalidateOtherCPUs(address, targetNode);
        }
    }

    private void loadBlock(String address, CacheBlock block, CPUNode targetNode) {
    	String tag = address.substring(2, 6); 
    	// 检查其他CPU的Cache中是否有该数据（状态为M或S）
        for (CPUNode node : cpuNodes) {
            if (node == targetNode) continue; // 跳过目标节点
            for (CacheBlock otherBlock : node.cacheBlocks) {
                if (otherBlock.tag.equals(tag) && otherBlock.state != CacheState.INVALID) {
                    block.data = otherBlock.data.clone(); 
                    // 更新状态：若来源为M，则变为S（因数据被共享），并通知来源写回主存
                    if (otherBlock.state == CacheState.MODIFIED) {
                        String mainAddress = "0x" + otherBlock.tag + otherBlock.index + otherBlock.offset;
                        mainMemory.put(mainAddress, otherBlock.data); // 写回主存
                        otherBlock.state = CacheState.SHARED;
                        otherBlock.isDirty = false;
                        System.out.println("CPU " + node.id + " 的Cache块写回主存");
                    }
                    block.state = CacheState.SHARED; // 本地变为共享
                    block.isDirty = false;
                    return;
                }
            }
        }
        
        // 从主存获取整个块数据（16字节）
        String[] mainData = mainMemory.computeIfAbsent(address, k -> {
            String[] zeros = new String[BLOCK_SIZE];
            Arrays.fill(zeros, "0");
            return zeros;
        });
        
        block.data = mainData.clone();
        block.state = CacheState.SHARED;
        block.isDirty = false;
    }
    
    // 作废其他CPU的相同地址缓存块
    private void invalidateOtherCPUs(String address, CPUNode excludeNode) {
        String tag = address.substring(2, 6); 
        for (CPUNode node : cpuNodes) {
            if (node == excludeNode) continue; // 跳过目标节点
            for (CacheBlock block : node.cacheBlocks) {
                if (block.tag.equals(tag)) {          // 标签相同表示缓存了同一主存块
                    block.state = CacheState.INVALID; // 作废
                }
            }
        }
    }
    
    // 写回主存
    private void writeBackToMainMemory(CacheBlock block) {
        if (block.tag.equals("-")) return; 	 // 无效标签不处理
        String address = "0x" + block.tag + block.index + block.offset;
        mainMemory.put(address, block.data); // 更新主存
        block.isDirty = false; 				 // 清除标记
        System.out.println("写回主存：" + address + "，数据：" + String.join("", block.data));
    }
    
    /*
     * 更新历史列表显示
     */
    private void updateHistoryList() {
        historyModel.clear();
        requestHistory.forEach(r -> historyModel.addElement(
            String.format("[%s] %s %s %s",
                (Boolean) r.get("cacheHit") ? "命中" : "缺失",
                r.get("targetCpu"),
                r.get("operation"),
                r.get("address")
            )
        ));
    }

    /*
     * 重新渲染CPU节点面板
     */
    private void renderCPUNodes() {
        cpuNodesPanel.removeAll();
        for (CPUNode node : cpuNodes) {
            JPanel cpuPanel = new JPanel(new BorderLayout());
            cpuPanel.setBorder(new TitledBorder(node.id));
            cpuPanel.add(new JScrollPane(new JTable(createCacheTableModel(node))), BorderLayout.CENTER);
            cpuNodesPanel.add(cpuPanel);
        }
        cpuNodesPanel.revalidate();
        cpuNodesPanel.repaint();
    }

    /*
     * 显示请求详情
     */
    private void showRequestDetails() {
        int index = historyList.getSelectedIndex();
        if (index == -1) return;

        Map<String, Object> request = requestHistory.get(index);
        detailArea.setText(String.format(
            "===== 请求详情 =====\n" +
            "地址: %s\n" +
            "操作: %s\n" +
            "目标节点: %s\n" +
            "Cache状态: %s\n" +
            "\n操作步骤:\n%s",
            request.get("address"),
            request.get("operation"),
            request.get("targetCpu"),
            (Boolean)request.get("cacheHit") ? "命中" : "缺失",
            request.get("details").toString().replace("[", "").replace("]", "")
        ));
    }

    /*
     * 重置系统状态
     */
    private void resetSystem() {
    	// 写回所有CPU的脏块
        for (CPUNode node : cpuNodes) {
            for (CacheBlock block : node.cacheBlocks) {
                if (block.state == CacheState.MODIFIED && block.isDirty) {
                    writeBackToMainMemory(block);
                }
            }
        }
        initSystem();
        historyModel.clear();
        detailArea.setText("");
        renderCPUNodes();
    }

    /*
     * 根据ID获取目标CPU节点
     */
    private CPUNode getTargetNode(String targetCpuId) {
        return Arrays.stream(cpuNodes)
                .filter(node -> node.id.equals(targetCpuId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("目标节点不存在"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Snoop().setVisible(true));
    }
}
