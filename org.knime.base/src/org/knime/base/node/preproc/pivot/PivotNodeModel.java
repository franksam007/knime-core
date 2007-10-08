/* 
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2007
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 * 
 * History
 *   03.05.2007 (gabriel): created
 */
package org.knime.base.node.pivot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.property.hilite.DefaultHiLiteHandler;
import org.knime.core.node.property.hilite.DefaultHiLiteMapper;
import org.knime.core.node.property.hilite.HiLiteHandler;
import org.knime.core.node.property.hilite.HiLiteTranslator;
import org.knime.core.util.Pair;


/**
 * The pivoting node uses on column as grouping (row header) and one as pivoting
 * column (column header) to aggregate a column by its values. One additional 
 * column (table content) can be selected the compute an aggregation value
 * for each pair of pivot and group value.
 * 
 * @see PivotAggregationMethod
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public class PivotNodeModel extends NodeModel {
    
    private final SettingsModelString m_group =
        PivotNodeDialogPane.createSettingsGroup();
    private final SettingsModelString m_pivot =
        PivotNodeDialogPane.createSettingsPivot();

    private final SettingsModelString m_agg =
        PivotNodeDialogPane.createSettingsAggregation();
    private final SettingsModelString m_aggMethod =
        PivotNodeDialogPane.createSettingsAggregationMethod();
    private final SettingsModelString m_makeAgg =
        PivotNodeDialogPane.createSettingsMakeAggregation();
    
    private final SettingsModelBoolean m_hiliting = 
        PivotNodeDialogPane.createSettingsEnableHiLite();

    /**
     * Node returns a new hilite handler instance.
     */
    private final HiLiteTranslator m_hilite = new HiLiteTranslator(
            new DefaultHiLiteHandler());

    
    /**
     * Creates a new pivot model with one in- and outport.
     */
    public PivotNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        int group = inSpecs[0].findColumnIndex(m_group.getStringValue());
        if (group < 0) {
            throw new InvalidSettingsException("Group column not found.");
        }
        int pivot = inSpecs[0].findColumnIndex(m_pivot.getStringValue());
        if (pivot < 0) {
            throw new InvalidSettingsException("Pivot column not found.");
        }
        if (m_makeAgg.getStringValue().equals(
                PivotNodeDialogPane.MAKE_AGGREGATION[1])) {
        int agg = inSpecs[0].findColumnIndex(m_agg.getStringValue());
            if (agg < 0) {
                throw new InvalidSettingsException(
                        "Aggregation column not found.");
            }
        }
        DataColumnSpec cspec = inSpecs[0].getColumnSpec(pivot);
        if (!cspec.getDomain().hasValues()) {
            return new DataTableSpec[1];
        } else {
            Set<DataCell> vals = cspec.getDomain().getValues();
            return new DataTableSpec[]{initSpec(vals)};
        }
    }

    /**
     * Creates a new DataTableSpec using the given possible values, each
     * of them as one double-type column.
     * @param vals possible values
     * @return possible values as DataTableSpec
     */
    private DataTableSpec initSpec(final Set<DataCell> vals) {
        String[] names = new String[vals.size()];
        DataType[] types = new DataType[vals.size()];
        int idx = 0;
        for (DataCell val : vals) {
            names[idx] = val.toString();
            types[idx] = DoubleCell.TYPE;
            idx++;
        }
        return new DataTableSpec(names, types);
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
        DataTableSpec inspec = inData[0].getDataTableSpec(); 
        int group = inspec.findColumnIndex(m_group.getStringValue());
        int pivot = inspec.findColumnIndex(m_pivot.getStringValue());
        int aggre = (m_makeAgg.getStringValue().equals(
                PivotNodeDialogPane.MAKE_AGGREGATION[1])
                ? inspec.findColumnIndex(m_agg.getStringValue()) : -1);
        PivotAggregationMethod aggMethod; 
        if (aggre < 0) {
            aggMethod = PivotAggregationMethod.COUNT;
        } else {
            aggMethod = PivotAggregationMethod.METHODS.get(
                        m_aggMethod.getStringValue());
        }
        // pair contains group and pivot plus the aggregation value
        Map<Pair<DataCell, DataCell>, Double[]> map = 
            new LinkedHashMap<Pair<DataCell, DataCell>, Double[]>();
        // list of pivot values
        Set<DataCell> pivotList = new LinkedHashSet<DataCell>();
        // list of group values
        Set<DataCell> groupList = new LinkedHashSet<DataCell>();
        final LinkedHashMap<DataCell, Set<DataCell>> mapping = 
            new LinkedHashMap<DataCell, Set<DataCell>>();
        double nrRows = inData[0].getRowCount();
        int rowCnt = 0;
        ExecutionContext subExec = exec.createSubExecutionContext(0.75);
        // final all group, pivot pair and aggregate the values of each group
        for (DataRow row : inData[0]) {
            subExec.checkCanceled();
            subExec.setProgress(++rowCnt / nrRows, 
                    "Aggregating row: \"" + row.getKey().getId() + "\" (" 
                    + rowCnt + "\\" + (int) nrRows + ")");
            DataCell groupCell = row.getCell(group);
            groupList.add(groupCell);
            DataCell pivotCell = row.getCell(pivot);
            pivotList.add(pivotCell);
            Pair<DataCell, DataCell> pair = 
                new Pair<DataCell, DataCell>(groupCell, pivotCell);
            Double[] aggValue = map.get(pair);
            if (aggValue == null) {
                aggValue = aggMethod.init();
                map.put(pair, aggValue);
            }
            if (aggre < 0) {
                aggMethod.compute(aggValue, null);
            } else {
                DataCell value = row.getCell(aggre);
                aggMethod.compute(aggValue, value);
            }
            if (m_hiliting.getBooleanValue()) {
                Set<DataCell> set = mapping.get(groupCell); 
                if (set == null) {
                    set = new LinkedHashSet<DataCell>();
                    mapping.put(groupCell, set);
                }
                set.add(row.getKey().getId());
            }
        }
        DataTableSpec outspec = initSpec(pivotList);
        // will contain the final pivoting table
        BufferedDataContainer buf = exec.createDataContainer(outspec);
        double nrElements = groupList.size();
        int elementCnt = 0;
        subExec = exec.createSubExecutionContext(0.25);
        for (DataCell groupCell : groupList) {
            subExec.checkCanceled();
            subExec.setProgress(++elementCnt / nrElements,
                    "Computing aggregation of group \"" + groupCell + "\" (" 
                    + elementCnt + "\\" + (int) nrElements + ")");
            // contains the aggregated values
            DataCell[] aggValues = new DataCell[pivotList.size()];
            int idx = 0; // pivot index
            for (DataCell pivotCell : pivotList) {
                Pair<DataCell, DataCell> newPair = 
                    new Pair<DataCell, DataCell>(groupCell, pivotCell);
                Double[] aggValue = map.get(newPair);
                aggValues[idx] = aggMethod.done(aggValue);
                idx++;
            }
            // create new row with the given group id and aggregation values
            buf.addRowToTable(new DefaultRow(groupCell, aggValues));
        }
        buf.close();
        if (m_hiliting.getBooleanValue()) {
            m_hilite.setMapper(new DefaultHiLiteMapper(mapping));
        }
        return new BufferedDataTable[]{buf.getTable()};
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void loadInternals(final File nodeInternDir, 
            final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        if (m_hiliting.getBooleanValue()) {
            NodeSettingsRO config = NodeSettings.loadFromXML(
                    new GZIPInputStream(new FileInputStream(
                    new File(nodeInternDir, "hilite_mapping.xml.gz"))));
            try {
                m_hilite.setMapper(DefaultHiLiteMapper.load(config));
            } catch (InvalidSettingsException ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_group.loadSettingsFrom(settings);
        m_pivot.loadSettingsFrom(settings);
        m_agg.loadSettingsFrom(settings);
        m_aggMethod.loadSettingsFrom(settings);
        m_makeAgg.loadSettingsFrom(settings);
        m_hiliting.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void reset() {
        m_hilite.getFromHiLiteHandler().fireClearHiLiteEvent();
        m_hilite.setMapper(null);
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void saveInternals(final File nodeInternDir, 
            final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        if (m_hiliting.getBooleanValue()) {
            NodeSettings config = new NodeSettings("hilite_mapping");
            ((DefaultHiLiteMapper) m_hilite.getMapper()).save(config);
            config.saveToXML(new GZIPOutputStream(new FileOutputStream(new File(
                    nodeInternDir, "hilite_mapping.xml.gz"))));
        }
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_group.saveSettingsTo(settings);
        m_pivot.saveSettingsTo(settings);
        m_agg.saveSettingsTo(settings);
        m_aggMethod.saveSettingsTo(settings);
        m_makeAgg.saveSettingsTo(settings);
        m_hiliting.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_group.validateSettings(settings);
        m_pivot.validateSettings(settings);
        m_agg.validateSettings(settings);
        m_aggMethod.validateSettings(settings);
        m_makeAgg.validateSettings(settings);
        m_hiliting.validateSettings(settings);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    protected void setInHiLiteHandler(final int inIndex, 
            final HiLiteHandler hiLiteHdl) {
        m_hilite.removeAllToHiliteHandlers();
        if (hiLiteHdl != null) {
            m_hilite.addToHiLiteHandler(hiLiteHdl);
        }
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    protected HiLiteHandler getOutHiLiteHandler(final int outIndex) {
        assert outIndex == 0;
        return m_hilite.getFromHiLiteHandler();
    }

}
