/*
 * --------------------------------------------------------------------- *
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
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
 */
package org.knime.base.node.mine.scorer.accuracy;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * The factory for the hilite scorer node.
 * 
 * @author Christoph Sieb, University of Konstanz
 */
public class AccuracyScorerNodeFactory 
        extends NodeFactory<AccuracyScorerNodeModel> {
    
    /**
     * {@inheritDoc}
     */
    @Override
    public AccuracyScorerNodeModel createNodeModel() {
        return new AccuracyScorerNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<AccuracyScorerNodeModel> createNodeView(
            final int i, final AccuracyScorerNodeModel nodeModel) {
        if (i == 0) {
            return new AccuracyScorerNodeView(nodeModel);
        } else {
            throw new IllegalArgumentException("No such view");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {

        return new AccuracyScorerNodeDialog();
    }
}
