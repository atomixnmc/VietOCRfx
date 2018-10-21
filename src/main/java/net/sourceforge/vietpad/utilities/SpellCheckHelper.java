/**
 * Copyright @ 2010 Quan Nguyen
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.vietpad.utilities;

import java.io.*;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.control.IndexRange;

import com.stibocatalog.hunspell.Hunspell;
import com.jfoenix.utils.JFXHighlighter1;
import com.sun.jna.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.TextArea;
import net.sourceforge.vietocr.util.Utils;

public class SpellCheckHelper {

    private static final String JNA_LIBRARY_PATH = "jna.library.path";
    TextArea textarea;
    // define the highlighter
    static JFXHighlighter1 highlighter = new JFXHighlighter1();
    String localeId;
    File baseDir;
    static List<InvalidationListener> lstList = new ArrayList<InvalidationListener>();
    Hunspell.Dictionary spellDict;
    static List<String> userWordList = new ArrayList<String>();
    static long mapLastModified = Long.MIN_VALUE;

    private final static Logger logger = Logger.getLogger(SpellCheckHelper.class.getName());

    /**
     * Constructor.
     *
     * @param textarea
     * @param localeId
     */
    public SpellCheckHelper(TextArea textarea, String localeId) {
        this.textarea = textarea;
        this.localeId = localeId;
        baseDir = Utils.getBaseDir(SpellCheckHelper.this);
//        highlighter = new JFXHighlighter1();
//        textarea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
//            javafx.application.Platform.runLater(()-> highlighter.highlight((Parent) this.textarea.lookup(".content"), ranges));
//        });
//
//        textarea.scrollLeftProperty().addListener((obs, oldVal, newVal) -> {
//            javafx.application.Platform.runLater(()-> highlighter.highlight((Parent) this.textarea.lookup(".content"), ranges));
//        });
    }

    public boolean initializeSpellCheck() {
        if (localeId == null) {
            return false;
        }

        try {
            if (Platform.isWindows()) {
                String hunspellDllLocation = baseDir.getPath() + "/lib/" + Platform.RESOURCE_PREFIX;
                System.setProperty(JNA_LIBRARY_PATH, hunspellDllLocation);
            }
            String dictPath = new File(baseDir, "dict/" + localeId).getPath();
            spellDict = Hunspell.getInstance().getDictionary(dictPath);
            if (!loadUserDictionary()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Enables spellcheck.
     */
    public void enableSpellCheck() {
        if (localeId == null) {
            return;
        }
        try {
            if (Platform.isWindows()) {
                String hunspellDllLocation = baseDir.getPath() + "/lib/" + Platform.RESOURCE_PREFIX;
                System.setProperty(JNA_LIBRARY_PATH, hunspellDllLocation);
            }
            spellDict = Hunspell.getInstance().getDictionary(new File(baseDir, "dict/" + localeId).getPath());
            loadUserDictionary();

//            SpellcheckListener docListener = new SpellcheckListener();
//            lstList.add(docListener);
//            this.textarea.textProperty().addListener(new WeakInvalidationListener(docListener));
            spellCheck();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
//            JOptionPane.showMessageDialog(null, e.getMessage(), Gui.APP_NAME, JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Disables spellcheck.
     */
    public void disableSpellCheck() {
        if (lstList.size() > 0) {
            this.textarea.textProperty().removeListener(lstList.remove(0));
            // remove All Highlights
            highlighter.clear();
        }
    }

    /**
     * Spellchecks.
     */
    public void spellCheck() {
        List<String> words = parseText(textarea.getText());
        List<String> misspelledWords = spellCheck(words);
        if (misspelledWords.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String word : misspelledWords) {
            sb.append(word).append("|");
        }
        sb.setLength(sb.length() - 1); //remove last |

        // build regex
        String patternStr = "\\b(" + sb.toString() + ")\\b";

        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textarea.getText());
        List<IndexRange> ranges = new ArrayList<IndexRange>();

        while (matcher.find()) {
            ranges.add(new IndexRange(matcher.start(), matcher.end()));
        }
        
        javafx.application.Platform.runLater(()-> highlighter.highlight((Parent) this.textarea.lookup(".content"), ranges));
    }

    /**
     * Spellchecks list of words.
     *
     * @param words
     * @return
     */
    List<String> spellCheck(List<String> words) {
        List<String> misspelled = new ArrayList<String>();

        for (String word : words) {
            if (spellDict.misspelled(word)) {
                // is misspelled word in user.dic?
                if (!userWordList.contains(word.toLowerCase())) {
                    misspelled.add(word);
                }
            }
        }

        return misspelled;
    }

    public boolean isMispelled(String word) {
        if (spellDict.misspelled(word)) {
            if (!userWordList.contains(word.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Parses input text.
     *
     * @param text
     * @return
     */
    List<String> parseText(String text) {
        List<String> words = new ArrayList<String>();
        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);
        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            if (!Character.isLetter(text.charAt(start))) {
                continue;
            }
            words.add(text.substring(start, end));
        }

        return words;
    }

    /**
     * Suggests words.
     *
     * @param misspelled
     * @return
     */
    public List<String> suggest(String misspelled) {
        List<String> list = new ArrayList<String>();
        list.add(misspelled);
        if (spellCheck(list).isEmpty()) {
            return null;
        } else {
            try {
                return spellDict.suggest(misspelled);
            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * Ignores word.
     *
     * @param word
     */
    public void ignoreWord(String word) {
        if (!userWordList.contains(word.toLowerCase())) {
            userWordList.add(word.toLowerCase());
        }
    }

    /**
     * Adds word to user dictionary.
     *
     * @param word
     */
    public void addWord(String word) {
        if (!userWordList.contains(word.toLowerCase())) {
            userWordList.add(word.toLowerCase());
            try {
                File userDict = new File(baseDir, "dict/user.dic");
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(userDict, true), "UTF8"));
                out.write(word);
                out.newLine();
                out.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        }
    }

    /**
     * Loads user dictionary only once.
     */
    boolean loadUserDictionary() {
        try {
            File userDict = new File(baseDir, "dict/user.dic");
            long fileLastModified = userDict.lastModified();

            if (fileLastModified <= mapLastModified) {
                return true;// no need to reload dictionary
            }

            mapLastModified = fileLastModified;
            userWordList.clear();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(userDict), "UTF8"));
            String str;
            while ((str = in.readLine()) != null) {
                userWordList.add(str.toLowerCase());
            }
            in.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return false;
        }
        return true;
    }

    class SpellcheckListener implements InvalidationListener {

        @Override
        public void invalidated(Observable observable) {
            spellCheck();
        }
    }
}
