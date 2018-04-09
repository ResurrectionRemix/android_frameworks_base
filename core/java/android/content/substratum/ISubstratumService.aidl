/*
 * Copyright (C) 2018 Projekt Substratum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.substratum;

interface ISubstratumService {

    /**
     * Install a list of specified overlay packages
     *
     * @param paths Filled in with a list of path names for packages to be installed from.
     */
    void installOverlay(in List<String> paths);

    /**
     * Uninstall a list of specified overlay packages
     *
     * @param packages  Filled in with a list of path names for packages to be installed from.
     * @param restartUi Flag to automatically restart the SystemUI.
     */
    void uninstallOverlay(in List<String> packages, boolean restartUi);

    /**
     * Switch the state of specified overlay packages
     *
     * @param packages  Filled in with a list of package names to be switched.
     * @param enable    Whether to enable the specified overlays.
     * @param restartUi Flag to automatically restart the SystemUI.
     */
    void switchOverlay(in List<String> packages, boolean enable, boolean restartUi);

    /**
     * Change the priority of a specified list of overlays
     *
     * @param packages  Filled in with a list of package names to be reordered.
     * @param restartUi Flag to automatically restart the SystemUI.
     */
    void setPriority(in List<String> packages, boolean restartUi);

    /**
     * Restart SystemUI
     */
    void restartSystemUI();

    /**
     * Copy Method
     *
     * @param source        Path of the source file.
     * @param destination   Path of the source file to be copied to.
     */
    void copy(String source, String destination);

    /**
     * Move Method
     *
     * @param source        Path of the source file.
     * @param destination   Path of the source file to be moved to.
     */
    void move(String source, String destination);

    /**
     * Create Directory Method
     *
     * @param destination   Path of the created destination folder.
     */
    void mkdir(String destination);

    /**
     * Delete Directory Method
     *
     * @param destination   Path of the directory to be deleted.
     * @param withParent    Flag to automatically delete the folder encompassing the folder.
     */
    void deleteDirectory(String directory, boolean withParent);

    /**
     * Apply a specified bootanimation
     *
     * @param name  Path to extract the bootanimation archive from.
     */
    void applyBootanimation(String name);

    /**
     * Apply a specified font pack
     *
     * @param name  Path to extract the font archive from.
     */
    void applyFonts(String pid, String fileName);

    /**
     * Apply a specified sound pack
     *
     * @param name  Path to extract the sounds archive from.
     */
    void applySounds(String pid, String fileName);

    /**
     * Profile Applicator
     *
     * @param enable     Filled in with a list of package names to be enabled.
     * @param disable    Filled in with a list of package names to be disabled.
     * @param name       Name of the profile to be applied.
     * @param restartUi  Flag to automatically restart the SystemUI.
     */
    void applyProfile(in List<String> enable, in List<String> disable, String name,
            boolean restartUi);

    /**
     * Apply a specified shutdownanimation
     *
     * @param name  Path to extract the shutdown archive from.
     *              Use null to clear applied custom shutdown
     */
    void applyShutdownAnimation(String name);

    /**
     * Returns information about all installed overlay packages for the
     * specified user. If there are no installed overlay packages for this user,
     * an empty map is returned (i.e. null is never returned). The returned map is a
     * mapping of target package names to lists of overlays. Each list for a
     * given target package is sorted in priority order, with the overlay with
     * the highest priority at the end of the list.
     *
     * @param uid The user to get the OverlayInfos for.
     * @return A Map<String, List<OverlayInfo>> with target package names
     *         mapped to lists of overlays; if no overlays exist for the
     *         requested user, an empty map is returned.
     */
    Map getAllOverlays(in int uid);
}

