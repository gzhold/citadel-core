"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { registerSchema, type RegisterInput } from "@citadel/api-contract";
import { ApiError } from "@/api/client";
import { register as registerAccount } from "@/api/auth";
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
  EMAIL_ALREADY_USED: "该邮箱已被注册",
};

export default function RegisterPage() {
  const router = useRouter();
  const setSession = useAuthStore((state) => state.setSession);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "", displayName: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      // 注册成功即创建默认工作区（OWNER），并直接进入应用
      // （API 的 register 与 react-hook-form 的 register 同名，导入时已重命名为 registerAccount）
      const response = await registerAccount(values);
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
        <CardTitle className="text-xl">创建 Citadel 账号</CardTitle>
        <CardDescription>注册即创建你的工作区（你将成为 Owner）</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="space-y-4">
          {formError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </p>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="displayName">昵称</Label>
            <Input
              id="displayName"
              autoComplete="name"
              placeholder="Alice"
              {...register("displayName")}
            />
            {errors.displayName ? (
              <p className="text-xs text-destructive">{errors.displayName.message}</p>
            ) : null}
          </div>

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
            <Label htmlFor="password">密码（至少 8 位）</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              {...register("password")}
            />
            {errors.password ? (
              <p className="text-xs text-destructive">{errors.password.message}</p>
            ) : null}
          </div>
        </CardContent>
        <CardFooter className="mt-6 flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "创建中…" : "创建账号"}
          </Button>
          <p className="text-sm text-muted-foreground">
            已有账号？{" "}
            <Link href="/login" className="font-medium underline underline-offset-4">
              直接登录
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
