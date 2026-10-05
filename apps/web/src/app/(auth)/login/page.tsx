"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { loginSchema, type LoginInput } from "@citadel/api-contract";
import { ApiError } from "@/api/client";
import { login } from "@/api/auth";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "邮箱或密码错误",
  UNAUTHENTICATED: "登录状态已失效，请重新登录",
};

export default function LoginPage() {
  const router = useRouter();
  const setSession = useAuthStore((state) => state.setSession);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const response = await login(values);
      setSession(response);
      router.push("/dashboard");
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(ERROR_MESSAGES[error.code] ?? error.message);
      } else {
        setFormError("网络异常，请稍后重试");
      }
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">登录 Citadel</CardTitle>
        <CardDescription>登录后进入你的工作区</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="space-y-4">
          {formError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </p>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="email">邮箱</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              {...register("email")}
            />
            {errors.email ? (
              <p className="text-xs text-destructive">{errors.email.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">密码</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              {...register("password")}
            />
            {errors.password ? (
              <p className="text-xs text-destructive">{errors.password.message}</p>
            ) : null}
          </div>
        </CardContent>
        <CardFooter className="mt-6 flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "登录中…" : "登录"}
          </Button>
          <p className="text-sm text-muted-foreground">
            还没有账号？{" "}
            <Link href="/register" className="font-medium underline underline-offset-4">
              免费注册
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
