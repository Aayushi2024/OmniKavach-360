package com.example.rakshak360;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class GhostClickService extends AccessibilityService {

    public static boolean triggerGuestMode = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Agar trigger ON nahi hai ya event kaam ka nahi hai, toh turant exit
        if (!triggerGuestMode || getRootInActiveWindow() == null) return;

        // "TYPE_WINDOW_CONTENT_CHANGED" par ye super fast kaam karega
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        // Dono targets ko ek sath check karega bina kisi delay ke
        // Pehle aakhri step dhoondhega, fir pehla
        if (!fastScan(rootNode, "switch to sambhav")) {
            fastScan(rootNode, "sambhav");
        }
    }

    private boolean fastScan(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;

        // Text aur Description match (Case insensitive aur fast)
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();

        if ((nodeText != null && nodeText.toString().toLowerCase().contains(target)) ||
                (nodeDesc != null && nodeDesc.toString().toLowerCase().contains(target))) {

            // Clickable parent dhoondhne ka sabse tez loop
            AccessibilityNodeInfo temp = node;
            while (temp != null) {
                if (temp.isClickable()) {
                    if (temp.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        // Agar final step "Switch" wala hai, toh hi trigger OFF karega
                        if (target.contains("switch")) {
                            triggerGuestMode = false;
                        }
                        return true;
                    }
                }
                temp = temp.getParent();
            }
        }

        // Children mein fast recursion
        for (int i = 0; i < node.getChildCount(); i++) {
            if (fastScan(node.getChild(i), target)) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {}
}