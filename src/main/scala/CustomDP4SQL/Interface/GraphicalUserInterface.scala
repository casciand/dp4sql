package CustomDP4SQL.Interface

import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.PrivacyModel.AttributePrivacyModel

object GraphicalUserInterface {
  private val loader: SchemaMapper = SchemaMapper()
  
  /**
   * Main method to demonstrate the SQL to Relational Algebra conversion.
   * Entry point for the SQL to Relational Algebra Converter UI.
   * Loads configuration and schema files, initializes the Swing UI.
   *
   * @param args Command-line arguments: [0] YAML schema file path, [1] SQL query
   */
  def main(args: Array[String]): Unit = {

    if (args.length < 1) {
      println("Usage: CalciteRelAlgebraConverterUI <config_file_path>")
      return
    }
//    val configPath = args(0)
//    val config = loader.mapConfig(configPath)
//    val queryNames = config.queries.keys.toArray
//
//    val yamlFilePath = config.file_paths("schema")
//    val rawSchemaDef = loader.mapSchema(yamlFilePath)
//
//    val schemaConstraintsFilePath = config.file_paths("schema_constraints")
//    val schemaConstraints = loader.mapSchemaConstraints(schemaConstraintsFilePath)
//    val schemaDef = RelAlgebraConverter.applyMaxFrequencyConstraints(rawSchemaDef, schemaConstraints)
//
//    val schemaDefinition_tableNames = schemaDef.relations.map(_.name).toArray
//
//    // Application main window
//    val frame = new JFrame("SQL to Relational Algebra Converter")
//    frame.setSize(800, 600)
//    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
//
//    //There are two main tabs: Schema and Converter
//    val tabbedPane = new JTabbedPane()
//    frame.getContentPane.add(tabbedPane)
//    frame.setVisible(true)
//
//    // ***************************** Schema panel *****************************
//    val schemaPanel = new JPanel(new BorderLayout())
//    tabbedPane.addTab("Schema", schemaPanel)
//
//    // Schema panel title
//    schemaPanel.add(new JLabel("Table Definition:"), BorderLayout.NORTH)
//
//    // Table List Panel - Display list of tables in the schema
//    val schemaPanel_tableList = new JList[String](schemaDefinition_tableNames)
//    schemaPanel_tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
//    schemaPanel_tableList.setFont(new Font("Monospaced", Font.PLAIN, 14))
//    schemaPanel_tableList.setFixedCellWidth(150)
//
//    // Table information panel - Display details of the selected table
//    // This panel will show the selected table details and constraints
//    val schemaPanel_tableInfoPanel = new JPanel()
//
//    // Table Detail Area - Display details of the selected table
//    val tableDetail = new JTextArea()
//    tableDetail.setEditable(false)
//
//    // Table Constraints Area - Display constraints of the selected table
//    val schemaConstraintsPanel = new JPanel(new FlowLayout)
//    schemaConstraintsPanel.setLayout(new BoxLayout(schemaConstraintsPanel, BoxLayout.Y_AXIS))
//    val scrollConstraints = new JScrollPane(schemaConstraintsPanel)
//    val constraintsLabel = new JLabel("Schema Constraints")
//    constraintsLabel.setBackground(java.awt.Color.LIGHT_GRAY)
//    constraintsLabel.setHorizontalAlignment(SwingConstants.CENTER)
//    schemaConstraintsPanel.add(constraintsLabel)
//    val constraintsTableModel = new javax.swing.table.DefaultTableModel(
//      Array[AnyRef]("Column Name", "Constraint Name", "Value"), 0
//    )
//    val constraintsTable = new JTable(constraintsTableModel)
//    constraintsTable.setFillsViewportHeight(true)
//    val constraintsTableScroll = new JScrollPane(constraintsTable)
//    schemaConstraintsPanel.add(constraintsTableScroll)
//
//    //show the table details and constraints in the table info panel
//    schemaPanel_tableInfoPanel.setLayout(new BoxLayout(schemaPanel_tableInfoPanel, BoxLayout.Y_AXIS))
//    schemaPanel_tableInfoPanel.add(tableDetail)
//    schemaPanel_tableInfoPanel.add(scrollConstraints)
//
//    schemaPanel.add(new JScrollPane(schemaPanel_tableList), BorderLayout.WEST)
//    schemaPanel.add(schemaPanel_tableInfoPanel, BorderLayout.CENTER)
//
//    // ***************************** Converter panel *****************************
//    val converterPanel = new JPanel(new BorderLayout())
//    tabbedPane.addTab("Converter", converterPanel)
//
//    // Schema panel title
//    converterPanel.add(new JLabel("SQL Query:"), BorderLayout.NORTH)
//
//    // Query List Panel - Display list of queries
//    val converterPanel_queryList = new JList[String](queryNames)
//    converterPanel_queryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
//    converterPanel_queryList.setFont(new Font("Monospaced", Font.PLAIN, 14))
//    converterPanel_queryList.setFixedCellWidth(150)
//
//    // Query Result Panel - Display the query statement and converted relational algebra
//    val selectedPredefinedSqlInput = new JTextArea(5, 60)
//    selectedPredefinedSqlInput.setLineWrap(true)
//    selectedPredefinedSqlInput.setWrapStyleWord(true)
//    val selectedPredefinedSqlInputScroll = new JScrollPane(selectedPredefinedSqlInput)
//
//    // Query Result Panel - Convert Selected Query to Relational Algebra Button
//    val selectedPredefinedConvertButton = new JButton("Convert to Relational Algebra")
//
//    // Query Result Panel - New SQL Query Input Area
//    val newSqlInput = new JTextArea(5, 60)
//    newSqlInput.setLineWrap(true)
//    newSqlInput.setWrapStyleWord(true)
//    val scrollNewInput = new JScrollPane(newSqlInput)
//
//    // Query Result Panel - Convert New SQL to Relational Algebra Button
//    val convertNewSqlButton = new JButton("Convert to Relational Algebra")
//
//    // Query Result Panel - Relational Algebra Tree Output Area
//    val relationalAlgebraTree = new JTextArea(15, 60)
//    relationalAlgebraTree.setEditable(false)
//    val relationalAlgebraTreeScroll = new JScrollPane(relationalAlgebraTree)
//
//    // Query Result Panel - Relational Algebra Table Output Area
//    val relTableArea = new JTextArea(8, 60)
//    relTableArea.setEditable(false)
//    val scrollRelTable = new JScrollPane(relTableArea)
//
//    // Query Result Panel - Stability Output Area
//    val stabilityArea = new JTextArea(2, 60)
//    stabilityArea.setEditable(false)
//    // val scrollStability = new JScrollPane(stabilityArea)
//
//    // Query Result Panel - Combine selected query components
//    val converterPanel_queryResult_selectedQuery = new JPanel(new BorderLayout())
//    converterPanel_queryResult_selectedQuery.add(new JLabel("Predefined SQL Input:"), BorderLayout.NORTH)
//    converterPanel_queryResult_selectedQuery.add(selectedPredefinedSqlInputScroll, BorderLayout.CENTER)
//    converterPanel_queryResult_selectedQuery.add(selectedPredefinedConvertButton, BorderLayout.SOUTH)
//
//    // Query Result Panel - Combine new query components
//    val converterPanel_queryResult_newQuery = new JPanel(new BorderLayout())
//    converterPanel_queryResult_newQuery.add(new JLabel("New SQL Input:"), BorderLayout.NORTH)
//    converterPanel_queryResult_newQuery.add(scrollNewInput, BorderLayout.CENTER)
//    converterPanel_queryResult_newQuery.add(convertNewSqlButton, BorderLayout.SOUTH)
//
//    // Query Result Panel - Combine all components
//    val converterPanel_queryResult = new JPanel(new FlowLayout(FlowLayout.LEFT))
//
//    converterPanel_queryResult.setLayout(new BoxLayout(converterPanel_queryResult, BoxLayout.Y_AXIS))
//    converterPanel_queryResult.add(converterPanel_queryResult_selectedQuery)
//    converterPanel_queryResult.add(converterPanel_queryResult_newQuery)
//    converterPanel_queryResult.add(new JLabel("Relational Algebra Tree Output:"))
//    converterPanel_queryResult.add(relationalAlgebraTreeScroll)
//    converterPanel_queryResult.add(new JLabel("Relational Algebra Table:"))
//    converterPanel_queryResult.add(scrollRelTable)
//    converterPanel_queryResult.add(stabilityArea)
//
//    converterPanel.add(new JScrollPane(converterPanel_queryList), BorderLayout.WEST)
//    converterPanel.add(converterPanel_queryResult, BorderLayout.CENTER)
//
//    // ***************************** Setup UI events handlers *****************************
//    // Process SQL and update all relevant UI components
//    def processSqlAndUpdateUI(sql: String): Unit = {
//      try {
//        val rootSchema = createCalciteSchema(schemaDef, config)
//        val rel = convertToRelAlgebra(sql, rootSchema)
//        val relString = relationalTreeToStringWithPlaceholders(rel)
//        val outputWithTableNames = relationalAlgebraWithTableNames(rel, relString)
//        relationalAlgebraTree.setText(outputWithTableNames)
//
//        // Convert RelNode to Table and display in relTableArea
//        val maybeTable = relNodeToTable(rel, schemaDef)
//        relTableArea.setText(
//          maybeTable.map { table =>
//            val columnsInfo = table.attributes.map { col =>
//              val params = Option(col.constraints).map { tp =>
//                tp.productElementNames.zip(tp.productIterator).collect {
//                  case (name, Some(value)) => s"$name=$value"
//                  case (name, value: Boolean) if value => s"$name=true"
//                }.mkString("{", ", ", "}")
//              }.getOrElse("")
//              s"${col.name}: ${col.datatype} $params"
//            }.mkString("\n")
//            s"Table: ${table.name}\nPrimary Key: ${table.identifier.mkString(", ")}\nColumns:\n$columnsInfo"
//          }.getOrElse("No TableScan node found in this query.")
//        )
//
//        val stability = calculateStability(rel, schemaDef)
//        stabilityArea.setText(s"Stability: $stability")
//      } catch {
//        case ex: Exception =>
//          relationalAlgebraTree.setText("Error: " + ex.getMessage)
//          relTableArea.setText("")
//          stabilityArea.setText("")
//      }
//    }
//
//    // Existing convert button uses the refactored function
//    selectedPredefinedConvertButton.addActionListener(_ => {
//      val sql = selectedPredefinedSqlInput.getText
//      if (sql.trim.nonEmpty) {
//        processSqlAndUpdateUI(sql)
//      } else {
//        relTableArea.setText("No schema information available.")
//        relationalAlgebraTree.setText("Please enter a SQL query.")
//      }
//    })
//
//    // Add another button for a different SQL string (e.g., first predefined query)
//
//    convertNewSqlButton.addActionListener(_ => {
//      val sql = newSqlInput.getText
//      if (sql.trim.nonEmpty) {
//        processSqlAndUpdateUI(sql)
//      } else {
//        relTableArea.setText("No schema information available.")
//        relationalAlgebraTree.setText("Please enter a SQL query.")
//      }
//    })
//
//    // Add a listener to update schemaDetail when a table is selected
//    schemaPanel_tableList.addListSelectionListener(new ListSelectionListener {
//      override def valueChanged(e: ListSelectionEvent): Unit = {
//        if (!e.getValueIsAdjusting) {
//          val selectedTable = schemaPanel_tableList.getSelectedValue
//          if (selectedTable != null) {
//            val tableOpt = schemaDef.relations.find(_.name == selectedTable)
//            tableOpt.foreach { table =>
//              val columnsInfo = table.attributes.map { col =>
//                val params = Option(col.constraints).map { tp =>
//                  tp.productElementNames.zip(tp.productIterator).collect {
//                    case (name, Some(value)) => s"$name=$value"
//                    case (name, value: Boolean) if value => s"$name=true"
//                  }.mkString("{", ", ", "}")
//                }.getOrElse("")
//                s"${col.name}: ${col.datatype} $params"
//              }.mkString("\n")
//              tableDetail.setText(
//                s"Table: ${table.name}\nPrimary Key: ${table.identifier.mkString(", ")}\nColumns:\n$columnsInfo"
//              )
//            }
//            schemaConstraints.get(selectedTable) match {
//              case Some(t) =>
//                updateConstraintsTableModel(constraintsTableModel, Some(t.columns))
//              case None =>
//                constraintsTableModel.setRowCount(0) // Clear if no constraints found
//            }
//          }
//        }
//      }
//    })
//
//    // When a query is selected, load its SQL into the input area
//    converterPanel_queryList.addListSelectionListener(new ListSelectionListener {
//      override def valueChanged(e: ListSelectionEvent): Unit = {
//        if (!e.getValueIsAdjusting) {
//          val selectedQuery = converterPanel_queryList.getSelectedValue
//          if (selectedQuery != null) {
//            val sql = config.queries(selectedQuery)
//            selectedPredefinedSqlInput.setText(sql)
//          }
//        }
//      }
//    })
//
//    // Keep the main thread alive
//    while (true) {
//      Thread.sleep(1000)
//    }
  }

  /**
   * Update the DefaultTableModel for constraints with the given list of ColumnConstraints.
   * Clears all rows and repopulates with the new data.
   *
   * @param model       JTable model to update
   * @param constraints Optional list of ColumnConstraints for a table
   */
  def updateConstraintsTableModel(
                                   model: javax.swing.table.DefaultTableModel,
                                   constraints: Option[List[AttributePrivacyModel]]
                                 ): Unit = {
    // Remove all existing rows from the table model
    while (model.getRowCount > 0) {
      model.removeRow(0)
    }
    // If constraints are provided, add each column's constraints to the table
    constraints match {
      case Some(cols) =>
        cols.foreach { col =>
          // Only process columns with non-null constraints_params
          if (col != null) {
            col.productElementNames.zip(col.productIterator).foreach {
              case (name, value) =>
                if (value != None && value != null) {
                  // Log the constraint parameter for debugging
                  println(s"Constraint param: $name = $value")
                  val valueStr: String = value match {
                    case null => ""
                    case Some(v) => String.valueOf(v)
                    case None => ""
                    case v => String.valueOf(v)
                  }
                  model.addRow(Array[AnyRef](col.name, name, valueStr))
                }
            }
          }
        }
      case None => // No constraints to add
    }
  }
}


