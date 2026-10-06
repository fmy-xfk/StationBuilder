// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package cn.myfrank.stationbuilder.vanilla;

import cn.myfrank.stationbuilder.utils.CurveData;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class VanillaCurveData implements CurveData {
   public static enum Shape {
      QUADRATIC,
      TWO_RADII,
      CABLE;
   }
   public enum Angle {
      E(0.0F),
      SEE(22.5F),
      SE(45.0F),
      SSE(67.5F),
      S(90.0F),
      SSW(112.5F),
      SW(135.0F),
      SWW(157.5F),
      W(180.0F),
      NWW(202.5F),
      NW(225.0F),
      NNW(247.5F),
      N(270.0F),
      NNE(292.5F),
      NE(315.0F),
      NEE(337.5F);

      public final float angleDegrees;
      public final double angleRadians;
      public final double sin;
      public final double cos;
      public final double tan;
      public final double halfTan;
      // private static final int DEGREES_IN_CIRCLE = 360;
      private static final int QUADRANTS = values().length;
      private static final float ANGLE_INCREMENT = 360.0F / (float)QUADRANTS;

      private Angle(float angleDegrees) {
         this.angleDegrees = normalizeAngle(angleDegrees);
         this.angleRadians = Math.toRadians((double)this.angleDegrees);
         this.sin = Math.sin(this.angleRadians);
         this.cos = Math.cos(this.angleRadians);
         this.tan = Math.tan(this.angleRadians);
         this.halfTan = Math.tan(this.angleRadians / (double)2.0F);
      }

      public Angle getOpposite() {
         switch (this.ordinal()) {
            case 1:
               return NWW;
            case 2:
               return NW;
            case 3:
               return NNW;
            case 4:
               return N;
            case 5:
               return NNE;
            case 6:
               return NE;
            case 7:
               return NEE;
            case 8:
               return E;
            case 9:
               return SEE;
            case 10:
               return SE;
            case 11:
               return SSE;
            case 12:
               return S;
            case 13:
               return SSW;
            case 14:
               return SW;
            case 15:
               return SWW;
            default:
               return W;
         }
      }

      public Angle getClosest45() {
         switch (this.ordinal()) {
            case 1:
            case 15:
               return E;
            case 2:
            case 4:
            case 6:
            case 8:
            case 10:
            case 12:
            case 14:
            default:
               return this;
            case 3:
            case 5:
               return S;
            case 7:
            case 9:
               return W;
            case 11:
            case 13:
               return N;
         }
      }

      public Angle add(Angle angle) {
         return fromAngle(this.angleDegrees + angle.angleDegrees);
      }

      public Angle sub(Angle angle) {
         return fromAngle(this.angleDegrees - angle.angleDegrees);
      }

      public boolean isParallel(Angle angle) {
         return this == angle || this == angle.getOpposite();
      }

      public boolean similarFacing(float newAngleDegrees) {
         return similarFacing(this.angleDegrees, newAngleDegrees);
      }

      public static boolean similarFacing(float angleDegrees1, float angleDegrees2) {
         return Math.abs(normalizeAngle(angleDegrees1 - angleDegrees2)) < 90.0F;
      }

      public static int getQuadrant(float angleDegrees, boolean include225) {
         int factor = include225 ? 1 : 2;
         return Math.round((normalizeAngle(angleDegrees) + 360.0F) / ANGLE_INCREMENT / (float)factor) % (QUADRANTS / factor);
      }

      public static Angle fromAngle(float angleDegrees) {
         return values()[getQuadrant(angleDegrees, true)];
      }

      private static float normalizeAngle(float angleDegrees) {
         int additional;
         for(additional = 0; angleDegrees + (float)additional < -180.0F; additional += 360) {
         }

         while(angleDegrees + (float)additional >= 180.0F) {
            additional -= 360;
         }

         return angleDegrees + (float)additional;
      }
   }

   public final long minX;
   public final long minY;
   public final long minZ;
   public final long maxY;
   public final long maxX;
   public final long maxZ;
   private final Shape shape;
   private final double verticalRadius;
   private final double h1;
   private final double k1;
   private final double h2;
   private final double k2;
   private final double r1;
   private final double r2;
   private final double tStart1;
   private final double tEnd1;
   private final double tStart2;
   private final double tEnd2;
   private final long yStart;
   private final long yEnd;
   private final boolean reverseT1;
   private final boolean reverseT2;
   private final boolean isStraight1;
   private final boolean isStraight2;
   // private static final double ACCEPT_THRESHOLD = 1.0E-4;
   // private static final int CABLE_CURVATURE_SCALE = 1000;
   // private static final int MAX_CABLE_DIP = 8;

   public VanillaCurveData(BlockPos position1, Angle angle1, BlockPos position2, Angle angle2, Shape shape, double verticalRadius) {
      long xStart = position1.getX();
      long zStart = position1.getZ();
      long xEnd = position2.getX();
      long zEnd = position2.getZ();
      Vec3d vecDifference = new Vec3d((double)(position2.getX() - position1.getX()), (double)0.0F, (double)(position2.getZ() - position1.getZ()));
      Vec3d vecDifferenceRotated = vecDifference.rotateY((float)angle1.angleRadians);
      double deltaForward = vecDifferenceRotated.z;
      double deltaSide = vecDifferenceRotated.x;
      if (angle1.isParallel(angle2)) {
         if (Math.abs(deltaForward) < 1.0E-4) {
            this.h1 = angle1.cos;
            this.k1 = angle1.sin;
            if (Math.abs(this.h1) >= (double)0.5F && Math.abs(this.k1) >= (double)0.5F) {
               this.r1 = (this.h1 * (double)zStart - this.k1 * (double)xStart) / this.h1 / this.h1;
               this.tStart1 = (double)xStart / this.h1;
               this.tEnd1 = (double)xEnd / this.h1;
            } else {
               double div = angle1.add(angle1).cos;
               this.r1 = (this.h1 * (double)zStart - this.k1 * (double)xStart) / div;
               this.tStart1 = (this.h1 * (double)xStart - this.k1 * (double)zStart) / div;
               this.tEnd1 = (this.h1 * (double)xEnd - this.k1 * (double)zEnd) / div;
            }

            this.h2 = this.k2 = this.r2 = (double)0.0F;
            this.reverseT1 = this.tStart1 > this.tEnd1;
            this.reverseT2 = false;
            this.isStraight1 = this.isStraight2 = true;
            this.tStart2 = this.tEnd2 = (double)0.0F;
         } else if (Math.abs(deltaSide) > 1.0E-4) {
            double radius = (deltaForward * deltaForward + deltaSide * deltaSide) / ((double)4.0F * deltaForward);
            this.r1 = this.r2 = Math.abs(radius);
            this.h1 = (double)xStart - radius * angle1.sin;
            this.k1 = (double)zStart + radius * angle1.cos;
            this.h2 = (double)xEnd - radius * angle2.sin;
            this.k2 = (double)zEnd + radius * angle2.cos;
            this.reverseT1 = deltaForward < (double)0.0F != deltaSide < (double)0.0F;
            this.reverseT2 = !this.reverseT1;
            this.tStart1 = getTBounds((double)xStart, this.h1, (double)zStart, this.k1, this.r1);
            this.tEnd1 = getTBounds((double)xStart + vecDifference.x / (double)2.0F, this.h1, (double)zStart + vecDifference.z / (double)2.0F, this.k1, this.r1, this.tStart1, this.reverseT1);
            this.tStart2 = getTBounds((double)xStart + vecDifference.x / (double)2.0F, this.h2, (double)zStart + vecDifference.z / (double)2.0F, this.k2, this.r2);
            this.tEnd2 = getTBounds((double)xEnd, this.h2, (double)zEnd, this.k2, this.r2, this.tStart2, this.reverseT2);
            this.isStraight1 = this.isStraight2 = false;
         } else {
            this.h1 = this.k1 = this.h2 = this.k2 = this.r1 = this.r2 = (double)0.0F;
            this.tStart1 = this.tStart2 = this.tEnd1 = this.tEnd2 = (double)0.0F;
            this.reverseT1 = false;
            this.reverseT2 = false;
            this.isStraight1 = this.isStraight2 = true;
         }
      } else {
         Angle newAngle1 = vecDifferenceRotated.x < -1.0E-4 ? angle1.getOpposite() : angle1;
         Angle newAngle2 = angle2.cos * vecDifference.x + angle2.sin * vecDifference.z < -1.0E-4 ? angle2.getOpposite() : angle2;
         double angleForward = Math.atan2(deltaForward, deltaSide);
         Angle railAngleDifference = newAngle2.sub(newAngle1);
         double angleDifference = railAngleDifference.angleRadians;
         if (Math.signum(angleForward) == Math.signum(angleDifference)) {
            double absAngleForward = Math.abs(angleForward);
            if (absAngleForward - Math.abs(angleDifference / (double)2.0F) < 1.0E-4) {
               double offsetSide = Math.abs(deltaForward / railAngleDifference.halfTan);
               double remainingSide = deltaSide - offsetSide;
               double deltaXEnd = (double)xStart + remainingSide * newAngle1.cos;
               double deltaZEnd = (double)zStart + remainingSide * newAngle1.sin;
               this.h1 = newAngle1.cos;
               this.k1 = newAngle1.sin;
               if (Math.abs(this.h1) >= (double)0.5F && Math.abs(this.k1) >= (double)0.5F) {
                  this.r1 = (this.h1 * (double)zStart - this.k1 * (double)xStart) / this.h1 / this.h1;
                  this.tStart1 = (double)xStart / this.h1;
                  this.tEnd1 = deltaXEnd / this.h1;
               } else {
                  double div = newAngle1.add(newAngle1).cos;
                  this.r1 = (this.h1 * (double)zStart - this.k1 * (double)xStart) / div;
                  this.tStart1 = (this.h1 * (double)xStart - this.k1 * (double)zStart) / div;
                  this.tEnd1 = (this.h1 * deltaXEnd - this.k1 * deltaZEnd) / div;
               }

               this.isStraight1 = true;
               this.reverseT1 = this.tStart1 > this.tEnd1;
               double radius = deltaForward / ((double)1.0F - railAngleDifference.cos);
               this.r2 = Math.abs(radius);
               this.h2 = deltaXEnd - radius * newAngle1.sin;
               this.k2 = deltaZEnd + radius * newAngle1.cos;
               this.reverseT2 = deltaForward < (double)0.0F;
               this.tStart2 = getTBounds(deltaXEnd, this.h2, deltaZEnd, this.k2, this.r2);
               this.tEnd2 = getTBounds((double)xEnd, this.h2, (double)zEnd, this.k2, this.r2, this.tStart2, this.reverseT2);
               this.isStraight2 = false;
            } else if (absAngleForward - Math.abs(angleDifference) < 1.0E-4) {
               double crossSide = deltaForward / railAngleDifference.tan;
               double remainingSide = (deltaSide - crossSide) * ((double)1.0F + railAngleDifference.cos);
               double remainingForward = (deltaSide - crossSide) * railAngleDifference.sin;
               double deltaXEnd = (double)xStart + remainingSide * newAngle1.cos - remainingForward * newAngle1.sin;
               double deltaZEnd = (double)zStart + remainingSide * newAngle1.sin + remainingForward * newAngle1.cos;
               double radius = (deltaSide - deltaForward / railAngleDifference.tan) / railAngleDifference.halfTan;
               this.r1 = Math.abs(radius);
               this.h1 = (double)xStart - radius * newAngle1.sin;
               this.k1 = (double)zStart + radius * newAngle1.cos;
               this.isStraight1 = false;
               this.reverseT1 = deltaForward < (double)0.0F;
               this.tStart1 = getTBounds((double)xStart, this.h1, (double)zStart, this.k1, this.r1);
               this.tEnd1 = getTBounds(deltaXEnd, this.h1, deltaZEnd, this.k1, this.r1, this.tStart1, this.reverseT1);
               this.h2 = newAngle2.cos;
               this.k2 = newAngle2.sin;
               if (Math.abs(this.h2) >= (double)0.5F && Math.abs(this.k2) >= (double)0.5F) {
                  this.r2 = (this.h2 * deltaZEnd - this.k2 * deltaXEnd) / this.h2 / this.h2;
                  this.tStart2 = deltaXEnd / this.h2;
                  this.tEnd2 = (double)xEnd / this.h2;
               } else {
                  double div = newAngle2.add(newAngle2).cos;
                  this.r2 = (this.h2 * deltaZEnd - this.k2 * deltaXEnd) / div;
                  this.tStart2 = (this.h2 * deltaXEnd - this.k2 * deltaZEnd) / div;
                  this.tEnd2 = (this.h2 * (double)xEnd - this.k2 * (double)zEnd) / div;
               }

               this.isStraight2 = true;
               this.reverseT2 = this.tStart2 > this.tEnd2;
            } else {
               this.h1 = this.k1 = this.h2 = this.k2 = this.r1 = this.r2 = (double)0.0F;
               this.tStart1 = this.tStart2 = this.tEnd1 = this.tEnd2 = (double)0.0F;
               this.reverseT1 = false;
               this.reverseT2 = false;
               this.isStraight1 = this.isStraight2 = true;
            }
         } else {
            this.h1 = this.k1 = this.h2 = this.k2 = this.r1 = this.r2 = (double)0.0F;
            this.tStart1 = this.tStart2 = this.tEnd1 = this.tEnd2 = (double)0.0F;
            this.reverseT1 = false;
            this.reverseT2 = false;
            this.isStraight1 = this.isStraight2 = true;
         }
      }

      this.yStart = position1.getY();
      this.yEnd = position2.getY();
      this.shape = shape;
      this.verticalRadius = Math.min(verticalRadius, this.getMaxVerticalRadius());
      double[] bounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
      this.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
         bounds[0] = Math.min(x1, bounds[0]);
         bounds[0] = Math.min(x2, bounds[0]);
         bounds[0] = Math.min(x3, bounds[0]);
         bounds[0] = Math.min(x4, bounds[0]);
         bounds[1] = Math.min(y1, bounds[1]);
         bounds[1] = Math.min(y2, bounds[1]);
         bounds[2] = Math.min(z1, bounds[2]);
         bounds[2] = Math.min(z2, bounds[2]);
         bounds[2] = Math.min(z3, bounds[2]);
         bounds[2] = Math.min(z4, bounds[2]);
         bounds[3] = Math.max(x1, bounds[3]);
         bounds[3] = Math.max(x2, bounds[3]);
         bounds[3] = Math.max(x3, bounds[3]);
         bounds[3] = Math.max(x4, bounds[3]);
         bounds[4] = Math.max(y1, bounds[4]);
         bounds[4] = Math.max(y2, bounds[4]);
         bounds[5] = Math.max(z1, bounds[5]);
         bounds[5] = Math.max(z2, bounds[5]);
         bounds[5] = Math.max(z3, bounds[5]);
         bounds[5] = Math.max(z4, bounds[5]);
      }, 0.1, 0.0F, 0.0F);
      this.minX = bounds[0] > bounds[3] ? 0L : (long)Math.floor(bounds[0]);
      this.minY = bounds[1] > bounds[4] ? 0L : (long)Math.floor(bounds[1]);
      this.minZ = bounds[2] > bounds[5] ? 0L : (long)Math.floor(bounds[2]);
      this.maxX = bounds[3] < bounds[0] ? 0L : (long)Math.ceil(bounds[3]);
      this.maxY = bounds[4] < bounds[1] ? 0L : (long)Math.ceil(bounds[4]);
      this.maxZ = bounds[5] < bounds[2] ? 0L : (long)Math.ceil(bounds[5]);
   }

   public Vec3d getPosition(double rawValue, boolean reverse) {
      double count1 = Math.abs(this.tEnd1 - this.tStart1);
      double count2 = Math.abs(this.tEnd2 - this.tStart2);
      double clampedValue = Math.min(Math.max(rawValue, (double)0.0F), count1 + count2);
      double value = reverse ? count1 + count2 - clampedValue : clampedValue;
      double y = this.getPositionY(value);
      return value <= count1 ? getPositionXZ(this.h1, this.k1, this.r1, (double)(this.reverseT1 ? -1 : 1) * value + this.tStart1, (double)0.0F, this.isStraight1).add((double)0.0F, y, (double)0.0F) : getPositionXZ(this.h2, this.k2, this.r2, (double)(this.reverseT2 ? -1 : 1) * (value - count1) + this.tStart2, (double)0.0F, this.isStraight2).add((double)0.0F, y, (double)0.0F);
   }

   public double getLength() {
      return Math.abs(this.tEnd2 - this.tStart2) + Math.abs(this.tEnd1 - this.tStart1);
   }

   public Shape getShape() {
      return this.shape;
   }

   public DoubleDoubleImmutablePair getHorizontalRadii() {
      return new DoubleDoubleImmutablePair(this.isStraight1 ? (double)0.0F : Math.abs(this.r1), this.isStraight2 ? (double)0.0F : Math.abs(this.r2));
   }

   public double getVerticalRadius() {
      return this.verticalRadius;
   }

   public double getMaxVerticalRadius() {
      double length = this.getLength();
      double height = (double)(this.yEnd - this.yStart);
      return Math.floor((length * length + height * height) * (double)100.0F / Math.abs((double)4.0F * height)) / (double)100.0F;
   }

   public void render(RenderRail callback, double interval, float offsetRadius1, float offsetRadius2) {
      this.renderSegment(this.h1, this.k1, this.r1, this.tStart1, this.tEnd1, (double)0.0F, interval, offsetRadius1, offsetRadius2, this.reverseT1, this.isStraight1, callback);
      this.renderSegment(this.h2, this.k2, this.r2, this.tStart2, this.tEnd2, Math.abs(this.tEnd1 - this.tStart1), interval, offsetRadius1, offsetRadius2, this.reverseT2, this.isStraight2, callback);
   }

   boolean isValid() {
      return this.h1 != (double)0.0F || this.k1 != (double)0.0F || this.h2 != (double)0.0F || this.k2 != (double)0.0F || this.r1 != (double)0.0F || this.r2 != (double)0.0F || this.tStart1 != (double)0.0F || this.tStart2 != (double)0.0F || this.tEnd1 != (double)0.0F || this.tEnd2 != (double)0.0F;
   }

   private void renderSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, double interval, float offsetRadius1, float offsetRadius2, boolean reverseT, boolean isStraight, RenderRail callback) {
      double count = Math.abs(tEnd - tStart);
      double increment = !(count < (double)0.5F) && !(interval <= (double)0.0F) ? count / (double)Math.round(count) * interval : (double)0.5F;
      Vec3d previousCorner1 = null;
      Vec3d previousCorner2 = null;
      double previousY = (double)0.0F;

      for(double i = (double)0.0F; i < count + increment - 0.1; i += increment) {
         double t = (double)(reverseT ? -1 : 1) * i + tStart;
         Vec3d corner1 = getPositionXZ(h, k, r, t, (double)offsetRadius2, isStraight);
         Vec3d corner2 = offsetRadius2 == offsetRadius1 ? corner1 : getPositionXZ(h, k, r, t, (double)offsetRadius1, isStraight);
         double y = this.getPositionY(i + rawValueOffset);
         if (previousCorner1 != null) {
            callback.renderRail(previousCorner1.x, previousCorner1.z, previousCorner2.x, previousCorner2.z, corner1.x, corner1.z, corner2.x, corner2.z, previousY, y);
         }

         previousCorner1 = corner2;
         previousCorner2 = corner1;
         previousY = y;
      }

   }

   private double getPositionY(double value) {
      if (this.yStart == this.yEnd) {
         return (double)this.yStart;
      } else {
         double length = this.getLength();
         switch (this.shape.ordinal()) {
            case 1:
               if (this.verticalRadius <= (double)0.0F) {
                  return value / length * (double)(this.yEnd - this.yStart) + (double)this.yStart;
               } else {
                  double vTheta = this.getVTheta();
                  double curveLength = Math.sin(vTheta) * this.verticalRadius;
                  double curveHeight = ((double)1.0F - Math.cos(vTheta)) * this.verticalRadius;
                  int sign = this.yStart < this.yEnd ? 1 : -1;
                  if (value < curveLength) {
                     return (double)sign * (this.verticalRadius - Math.sqrt(this.verticalRadius * this.verticalRadius - value * value)) + (double)this.yStart;
                  } else {
                     if (value > length - curveLength) {
                        double r = length - value;
                        return (double)(-sign) * (this.verticalRadius - Math.sqrt(this.verticalRadius * this.verticalRadius - r * r)) + (double)this.yEnd;
                     }

                     return (double)sign * ((value - curveLength) / (length - (double)2.0F * curveLength) * ((double)Math.abs(this.yEnd - this.yStart) - (double)2.0F * curveHeight) + curveHeight) + (double)this.yStart;
                  }
               }
            case 2:
               if (value < (double)0.5F) {
                  return (double)this.yStart;
               } else {
                  if (value > length - (double)0.5F) {
                     return (double)this.yEnd;
                  }

                  double cableOffsetValue = value - (double)0.5F;
                  double offsetLength = length - (double)1.0F;
                  double posY = (double)this.yStart + (double)(this.yEnd - this.yStart) * cableOffsetValue / offsetLength;
                  double dip = offsetLength * offsetLength / (double)4.0F / (double)1000.0F;
                  return posY + (dip > (double)8.0F ? (double)8.0F / dip : (double)1.0F) * (cableOffsetValue - offsetLength) * cableOffsetValue / (double)1000.0F;
               }
            default:
               double intercept = length / (double)2.0F;
               double yChange;
               double yInitial;
               double offsetValue;
               if (value < intercept) {
                  yChange = (double)(this.yEnd - this.yStart) / (double)2.0F;
                  yInitial = (double)this.yStart;
                  offsetValue = value;
               } else {
                  yChange = (double)(this.yStart - this.yEnd) / (double)2.0F;
                  yInitial = (double)this.yEnd;
                  offsetValue = length - value;
               }

               return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
         }
      }
   }

   private double getVTheta() {
      double height = (double)Math.abs(this.yEnd - this.yStart);
      double length = this.getLength();
      return (double)2.0F * Math.atan2(Math.sqrt(height * height - (double)4.0F * this.verticalRadius * height + length * length) - length, height - (double)4.0F * this.verticalRadius);
   }

   private static Vec3d getPositionXZ(double h, double k, double r, double t, double radiusOffset, boolean isStraight) {
      return !isStraight ? new Vec3d(h + (r + radiusOffset) * Math.cos(t / r) + (double)0.5F, (double)0.0F, k + (r + radiusOffset) * Math.sin(t / r) + (double)0.5F) : new Vec3d(h * t + k * ((Math.abs(h) >= (double)0.5F && Math.abs(k) >= (double)0.5F ? (double)0.0F : r) + radiusOffset) + (double)0.5F, (double)0.0F, k * t + h * (r - radiusOffset) + (double)0.5F);
   }

   private static double getTBounds(double x, double h, double z, double k, double r) {
      return Math.atan2(z - k, x - h) * r;
   }

   private static double getTBounds(double x, double h, double z, double k, double r, double tStart, boolean reverse) {
      double t = getTBounds(x, h, z, k, r);
      if (t < tStart && !reverse) {
         return t + (Math.PI * 2D) * r;
      } else {
         return t > tStart && reverse ? t - (Math.PI * 2D) * r : t;
      }
   }

   @FunctionalInterface
   public interface RenderRail {
      void renderRail(double var1, double var3, double var5, double var7, double var9, double var11, double var13, double var15, double var17, double var19);
   }

   @Override
   public Vec3d getPosition(double s) {
      return this.getPosition(s, false);
   }

   public static ObjectObjectImmutablePair<Angle, Angle> getAngles(BlockPos positionStart, float angle1, BlockPos positionEnd, float angle2) {
      float angleDifference = (float)Math.toDegrees(Math.atan2((double)(positionEnd.getZ() - positionStart.getZ()), (double)(positionEnd.getX() - positionStart.getX())));
      return new ObjectObjectImmutablePair<>(Angle.fromAngle(angle1 + (float)(Angle.similarFacing(angleDifference, angle1) ? 0 : 180)), Angle.fromAngle(angle2 + (float)(Angle.similarFacing(angleDifference, angle2) ? 180 : 0)));
   }
}
