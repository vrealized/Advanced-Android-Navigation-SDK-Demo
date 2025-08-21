/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.mapdemo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.Cap;
import com.google.android.gms.maps.model.CustomCap;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.SquareCap;
import java.util.HashMap;
import java.util.Map;

/** Fragment with "cap" UI controls for Polylines, to be used in ViewPager. */
public class PolylineCapControlFragment extends PolylineControlFragment
    implements RadioGroup.OnCheckedChangeListener {
  // We require Ook's refWidth > chevron's refWidth so isOok(CustomCap) can distinguish
  // between the two CustomCaps by simple comparison of CustomCap.getBitmapRefWidth() against
  // CHEVRON_VERSUS_OOK_REF_WIDTH_THRESHOLD, for the purpose of refreshing "cap" UI radio
  // buttons. See isOok() for details on why this is necessary.
  private static final float CHEVRON_REF_WIDTH = 15.0f;
  private static final float OOK_REF_WIDTH = 32.0f;
  private static final float CHEVRON_VERSUS_OOK_REF_WIDTH_THRESHOLD =
      0.5f * (CHEVRON_REF_WIDTH + OOK_REF_WIDTH);

  private final Map<Integer, Cap> radioIdToStartCap = new HashMap<>();
  private final Map<Integer, Cap> radioIdToEndCap = new HashMap<>();

  private RadioGroup startCapRadioGroup;
  private RadioGroup endCapRadioGroup;

  public PolylineCapControlFragment() {
    radioIdToStartCap.put(R.id.start_cap_radio_butt, new ButtCap());
    radioIdToStartCap.put(R.id.start_cap_radio_square, new SquareCap());
    radioIdToStartCap.put(R.id.start_cap_radio_round, new RoundCap());
    radioIdToStartCap.put(
        R.id.start_cap_radio_custom_chevron,
        new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.chevron), CHEVRON_REF_WIDTH));
    radioIdToStartCap.put(
        R.id.start_cap_radio_custom_ook,
        new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.ook), OOK_REF_WIDTH));

    radioIdToEndCap.put(R.id.end_cap_radio_butt, new ButtCap());
    radioIdToEndCap.put(R.id.end_cap_radio_square, new SquareCap());
    radioIdToEndCap.put(R.id.end_cap_radio_round, new RoundCap());
    radioIdToEndCap.put(
        R.id.end_cap_radio_custom_chevron,
        new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.chevron), CHEVRON_REF_WIDTH));
    radioIdToEndCap.put(
        R.id.end_cap_radio_custom_ook,
        new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.ook), OOK_REF_WIDTH));
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View view = inflater.inflate(R.layout.cap_control_fragment, container, false);

    startCapRadioGroup = (RadioGroup) view.findViewById(R.id.start_cap_radio);
    endCapRadioGroup = (RadioGroup) view.findViewById(R.id.end_cap_radio);

    startCapRadioGroup.setOnCheckedChangeListener(this);
    endCapRadioGroup.setOnCheckedChangeListener(this);
    return view;
  }

  @Override
  public void onCheckedChanged(RadioGroup group, int checkedId) {
    if (polyline == null) {
      return;
    }

    int groupId = group.getId();
    if (groupId == R.id.start_cap_radio) {
      Cap startCap = radioIdToStartCap.get(checkedId);
      if (startCap != null) {
        polyline.setStartCap(startCap);
      }
    } else if (groupId == R.id.end_cap_radio) {
      Cap endCap = radioIdToEndCap.get(checkedId);
      if (endCap != null) {
        polyline.setEndCap(endCap);
      }
    }
  }

  @Override
  public void refresh() {
    if (polyline == null) {
      startCapRadioGroup.clearCheck();
      for (int i = 0; i < startCapRadioGroup.getChildCount(); i++) {
        startCapRadioGroup.getChildAt(i).setEnabled(false);
      }
      endCapRadioGroup.clearCheck();
      for (int i = 0; i < endCapRadioGroup.getChildCount(); i++) {
        endCapRadioGroup.getChildAt(i).setEnabled(false);
      }
      return;
    }

    for (int i = 0; i < startCapRadioGroup.getChildCount(); i++) {
      startCapRadioGroup.getChildAt(i).setEnabled(true);
    }
    Cap startCap = polyline.getStartCap();
    if (startCap instanceof ButtCap) {
      startCapRadioGroup.check(R.id.start_cap_radio_butt);
    } else if (startCap instanceof SquareCap) {
      startCapRadioGroup.check(R.id.start_cap_radio_square);
    } else if (startCap instanceof RoundCap) {
      startCapRadioGroup.check(R.id.start_cap_radio_round);
    } else if (startCap instanceof CustomCap) {
      startCapRadioGroup.check(
          isOok((CustomCap) startCap)
              ? R.id.start_cap_radio_custom_ook
              : R.id.start_cap_radio_custom_chevron);
    } else {
      startCapRadioGroup.clearCheck();
    }

    for (int i = 0; i < endCapRadioGroup.getChildCount(); i++) {
      endCapRadioGroup.getChildAt(i).setEnabled(true);
    }
    Cap endCap = polyline.getEndCap();
    if (endCap instanceof ButtCap) {
      endCapRadioGroup.check(R.id.end_cap_radio_butt);
    } else if (endCap instanceof SquareCap) {
      endCapRadioGroup.check(R.id.end_cap_radio_square);
    } else if (endCap instanceof RoundCap) {
      endCapRadioGroup.check(R.id.end_cap_radio_round);
    } else if (endCap instanceof CustomCap) {
      endCapRadioGroup.check(
          isOok((CustomCap) endCap)
              ? R.id.end_cap_radio_custom_ook
              : R.id.end_cap_radio_custom_chevron);
    } else {
      endCapRadioGroup.clearCheck();
    }
  }

  private static boolean isOok(CustomCap customCap) {
    // Weird but seemingly the only plausible way to distinguish between the two specific
    // CustomCaps in use for this particular demo app, given a CustomCap returned by
    // Polyline.get{Start,End}Cap() (all due to AIDL bridge & BitmapDescriptor impl):
    // - Polyline.get{Start,End}Cap() returns a different CustomCap object every time.
    // - CustomCap.getBitmapDescriptor() returns different BitmapDescriptor obj every time.
    // - CustomCap.getBitmapDescriptor() gives useless BitmapDescriptor (no gettable data)
    // - BitmapDescriptor.equals() isn't implemented properly, and probably hard to be done.
    // - CustomCap.equals() is useless due to BitmapDescriptor.equals().
    return (customCap.refWidth >= CHEVRON_VERSUS_OOK_REF_WIDTH_THRESHOLD);
  }

  @Override
  public void onDestroyView() {
    startCapRadioGroup.setOnCheckedChangeListener(null);
    endCapRadioGroup.setOnCheckedChangeListener(null);

    startCapRadioGroup = null;
    endCapRadioGroup = null;

    super.onDestroyView();
  }
}
