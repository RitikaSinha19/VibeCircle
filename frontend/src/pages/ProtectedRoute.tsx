import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import useAuthStore from "../store/authStore";
import { profilePreferences } from "../api/profilePreferences";

interface ProtectedRouteProps {
  children: ReactNode;
  requireOnboarding?: boolean;
}

export default function ProtectedRoute({
  children,
  requireOnboarding = true,
}: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (
    requireOnboarding &&
    user?.id !== undefined &&
    user?.id !== null &&
    !profilePreferences.isComplete(user.id)
  ) {
    return <Navigate to="/profile-setup" replace />;
  }

  return <>{children}</>;
}
